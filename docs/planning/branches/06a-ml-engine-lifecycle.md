# hardening/06a-ml-engine-lifecycle

Priority 6 (part a) from `docs/planning/production-hardening.md`: ml-engine gets a real application lifecycle. Stacked on `hardening/05-kafka-kraft` (the integration branch).

## What changed

**Every environment read moves to `app/settings.py`.** One pydantic-settings class per backend (`SentimentSettings` with `env_prefix="STREAMSENSE_SENTIMENT_"`, `RelevanceSettings`, `SegmentationSettings`, `WhisperSettings`, `FrameStorageSettings`, `SponsorSettings`) composed into `Settings`. Variable names are unchanged, including the pre-rename `STREAMSENSE_SPONSOR_*` aliases for segmentation (`AliasChoices`), and blank values still mean "use the default". Two behaviour changes are deliberate: an unparseable number now fails start-up instead of being silently replaced by the default, and the frame-storage keys accept `<NAME>_FILE` inside the settings class rather than in `frame_store.py`. The backend modules keep their frozen config dataclasses (`SentimentConfig`, `RelevanceConfig`, `SegmentationConfig`, new `WhisperConfig`) but lose their `from_env()` constructors, `_env_*` helpers, and module-level singletons.

**`BackendRegistry` (`app/registry.py`) owns the models.** It is built once in the FastAPI lifespan, constructs every analyzer from the settings, warms up the ones whose `preload` flag is set, and flips `ready`. Each backend loads its model behind a `threading.Lock` (double-checked), so concurrent first requests cannot double-load, and exposes `is_loaded()` for `/ml/info`. The SAM segmenter now attempts to build its mask generator once per process; previously `propose_regions()` called `create_segmenter()` on every `/ml/segment` request, which rebuilt the segmenter and, without a cached checkpoint, re-downloaded 375 MB per call. The boto3 client in `FrameStore` is created once per process and closed on shutdown instead of once per request. Nothing loads or connects at import time.

**`create_app()` (`app/main.py`).** Routes receive `Settings` and the registry through dependencies (`SettingsDep`, `RegistryDep`); `get_registry` raises `ModelNotReady` (503) until the lifespan has run. `ML_ENGINE_FORCE_FAILURE` is a router-level dependency on the inference router, so it now covers `/ml/segment` as well (it was missed before). `FrameArtifactError` and `TranscriptionError` are mapped by exception handlers to the same 503 bodies the Java clients already expect, instead of being caught and re-raised as `HTTPException` in each route. `/ml/transcribe` is a plain `def`: FastAPI runs it in the thread pool, so a multi-second Whisper decode no longer blocks the event loop and starves `/ml/health`. The module-level `app = create_app()` keeps `uvicorn app.main:app` working.

**Operational endpoints.** `/ml/live` (always 200), `/ml/ready` (503 `starting` until the registry exists, then 200), `/ml/info` (service version, git sha, force-failure flag, and per-backend name, model, and `loaded` state), `/ml/health` kept for existing callers with an added `ready` field, and `/metrics` (an `http_request_duration_seconds` histogram keyed by route template from a small middleware, plus `streamsense_ml_inference_seconds{backend}` histograms and failure counters). Compose's healthcheck and the Kubernetes readiness and startup probes use `/ml/ready`; liveness uses `/ml/live`. Prometheus scrapes `ml-engine:8000` in both the Compose config and the Kubernetes ConfigMap; it never did before.

**Tests are rewritten around the factory.** `conftest.py` builds an app from explicit `Settings` (lexical sentiment, direct relevance, no segmentation, so nothing downloads) and injects `FakeRegistry` through `app.dependency_overrides[get_registry]`; no test monkeypatches a module global any more. New coverage: settings parsing and aliases (`test_settings.py`), live/ready/info/metrics and the pre-startup 503 (`test_operations.py`), force-failure across all five inference routes, single-load under eight concurrent threads for Whisper, SAM attempting its load once, and `FrameStore` behaviour without S3 config. `test_health.py` is folded into `test_operations.py`; `src/test/python/__init__.py` is removed so `conftest` imports as a top-level module.

**Dependencies**: `pydantic-settings` and `prometheus-client`; `uv.lock` re-resolved.

## Deliberately left alone

- `src/main/python/app` layout and `PYTHONPATH` in the image. The move to an installed `src/<package>` is mechanical and touches every import path; it is better as its own small branch after 06b so both services move together.
- No non-root `USER` in the Dockerfile yet. The model caches on the Compose named volumes were created by root; switching users needs a one-time ownership fix for existing volumes (`make nuke` or a chown init) and belongs with that layout branch.
- Structured JSON logging and mypy are not in this branch.
- Inference concurrency is bounded only by FastAPI's thread pool (40). A per-backend semaphore is a small follow-up once there is a measured need.

## Verification

| Check | Command | Result |
|---|---|---|
| Lock resolves with the three new packages | `uv lock` in `python:3.11.16-slim` | resolves; pydantic-settings and prometheus-client added (prometheus-fastapi-instrumentator was tried and dropped: its 7.x release breaks on FastAPI 0.141's router objects, so HTTP metrics are a 40-line middleware in `app/metrics.py` instead) |
| Lint | `uv run ruff check src/main/python src/test/python` | all checks passed |
| Tests | `uv run pytest` (full suite, no model downloads) | 67 passed, 1 skipped (the opt-in real-model test), up from 45 |
| Compose renders | `docker compose config -q` | OK |
| Kubernetes renders | `kubectl kustomize .` | OK |
| Image smoke | `docker build ml-engine`, run with lexical/direct backends, hit `/ml/ready`, `/ml/info`, `/ml/sentiment`, `/metrics` | IMAGE_PLACEHOLDER |

## Manual checks for the reviewer

1. `make up`: `curl localhost:8000/ml/ready` returns `{"status":"ready"}` and `curl localhost:8000/ml/info` lists five backends with `loaded: false` until first use (or `true` for any backend with `STREAMSENSE_<BACKEND>_PRELOAD=true`).
2. `make replay-smoke` passes; sentiment and transcript events still flow.
3. `curl localhost:8000/metrics | grep streamsense_ml_inference_seconds` shows per-backend histograms after traffic; Prometheus at `http://localhost:9090/targets` lists `ml-engine` as UP.
4. Post two `/ml/segment` requests with `STREAMSENSE_SEGMENTATION_BACKEND=sam` and no cached checkpoint: the download happens once, not twice (watch the ml-engine log).
5. While a `/ml/transcribe` request is running, `curl localhost:8000/ml/live` answers immediately.
6. `ML_ENGINE_FORCE_FAILURE=true docker compose up -d ml-engine`: all five inference endpoints return 503 with `forced ml-engine failure`, and `/ml/info` shows `"forceFailure": true`.

## Follow-ups (not in this branch)

- 06b applies the same lifecycle shape to video-capture-service.
- A layout branch: `src/streamsense_ml/` package, non-root image, mypy.
