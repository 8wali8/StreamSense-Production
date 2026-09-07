# hardening/06b-video-capture-lifecycle

Priority 6 (part b) from `docs/planning/production-hardening.md`: video-capture-service gets the same lifecycle discipline as ml-engine. Stacked on `hardening/06a-ml-engine-lifecycle`.

## What changed

**Nothing happens at import time.** `app/main.py` is a `create_app()` factory with a lifespan. Previously importing the module read and validated the configuration, opened a boto3 client that retried `head_bucket` thirty times, and created a `KafkaProducer`; running the tests needed those to be tolerated. Now a `CaptureRuntime` is built inside the lifespan: it reads the config (or takes one passed to the factory), creates the storage backend, connects the Kafka publishers, and starts the capture threads. `stop()` sets every worker's stop event, joins, and closes the producers. The module-level `app = create_app()` keeps `uvicorn app.main:app` working.

**One producer per topic, idempotent, no per-message flush.** `kafka_publisher.py` collapses two copy-pasted classes into `EventPublisher` (the old names remain as aliases). The kafka-python producer is created lazily or at start-up (`connect()`), with `enable_idempotence=True`, `acks=all`, bounded `retries` and `request_timeout_ms`. `publish()` still waits for the record's acknowledgement, so the status counters stay truthful, but the `flush()` after every send is gone; `close()` flushes once. A `producer_factory` hook lets tests substitute a fake without a broker.

**Failures are classified by type, not by class name.** The capture loop previously did `if "Kafka" in exc.__class__.__name__`. It now catches `kafka.errors.KafkaError` (state `DEGRADED_KAFKA`), then `botocore` `BotoCoreError`/`ClientError`, boto3's own `Boto3Error` (the transfer layer wraps a failed `upload_file` in `S3UploadFailedError`, which is not a botocore exception), and `OSError` (state `DEGRADED_STORAGE`), and finally a labelled `except Exception` at the worker boundary that logs a full traceback and counts the frame as `unexpected`. The transcript path gets the same split. The `BLE001` per-file ignore stays only for those two boundary handlers.

**Workers own their stop events.** `CaptureManager` keeps `(thread, event)` pairs; `switch_channels` no longer replaces a shared event while old loops may still be running. Threads are named `capture-<channel>`, and `workers_alive()` feeds readiness.

**Subprocesses cannot outlive a timeout.** `app/process.py::run_bounded` runs ffmpeg and streamlink in their own session (`start_new_session=True`) and, on timeout, kills the whole process group before re-raising `subprocess.TimeoutExpired`. It returns a standard `CompletedProcess`, so the three callers changed by one line each and their tests now patch `run_bounded` instead of `subprocess.run`.

**Readiness reflects the workers, against the active channel list.** The expected worker count comes from the manager's configuration, which a channel switch replaces, so switching from one channel to two reports `workersAlive: 2, workersExpected: 2` rather than a permanent 503. `/live` (always 200), `/ready` (503 `starting` before the lifespan; with capture disabled `ready`; with capture enabled 200 only while every configured channel's thread is alive, otherwise 503 `degraded` with the counts), `/health` kept for existing callers with a `ready` flag. Kubernetes readiness uses `/ready` and liveness `/live`; the Compose healthcheck uses `/ready` so dependants wait for the workers. The frame-proxy endpoint reuses the storage backend's boto3 client instead of building one per request, and returns 409 when the runtime is not on S3 storage.

**Tests**: `test_app.py` (no-side-effect import, readiness before and after start-up, disabled-capture behaviour, frame endpoint path checks), `test_process.py` (real subprocess success and timeout kill, bounded to well under the child's sleep), `test_capture_loop.py` (one loop iteration with fakes for resolver, sampler, storage, and publisher: success, Kafka error, storage `ClientError`, unexpected error, and independent stop events across `switch_channels`), publisher tests for lazy connect, idempotent config, no per-message flush, and flush-on-close. `httpx` joins the dev group for `TestClient`.

## Deliberately left alone

- `config.py` stays a dataclass with `from_env()` and `validate()`. It already centralises every env read and fails fast at start-up; converting it to pydantic-settings would mostly move code around and is not needed for the lifecycle goal. The replay-alias parsing (`STREAMSENSE_REPLAY_<KEY>_*`) is dynamic and fits a custom parser better anyway.
- streamlink is still invoked as a CLI. Using its Python API is a behaviour change worth its own test fixture against real plugin errors.
- Threads stay `daemon=True` so a stuck ffmpeg cannot block process exit; `stop()` joins them with a bound.
- The layout and non-root image move together with ml-engine in the layout branch.

## Verification

| Check | Command | Result |
|---|---|---|
| Lock resolves (httpx added to dev) | `uv lock` in `python:3.11.16-slim` | resolves |
| Lint | `uv run ruff check src/main/python src/test/python` | all checks passed |
| Tests | `uv run pytest` | 36 passed, up from 23 |
| Compose renders | `docker compose config -q` | OK |
| Kubernetes renders | `kubectl kustomize .` | OK |
| Image smoke | build, run with capture disabled, hit `/ready`, `/health`, status, channel switch (409), `/metrics` | image builds (c31f5c61…); with capture disabled `/ready` is 200 `{"status":"ready","capture":"disabled"}`, `/health` reports `ready: true`, the status snapshot shows `DISABLED`, the channel switch returns 409, `/metrics` serves, and the only start-up log line is "Twitch video capture disabled" (no Kafka or MinIO connection attempted) |

## Manual checks for the reviewer

1. `make up` with the replay alias: `curl localhost:8090/ready` returns `{"status":"ready","workersAlive":1,"workersExpected":1}` once capture is running; `make replay-smoke` passes.
2. `docker compose stop kafka` for a minute: the channel status moves to `DEGRADED_KAFKA` (not `DEGRADED_STORAGE`), the worker stays alive, `/ready` stays 200; frames resume after `docker compose start kafka`.
3. `docker compose stop minio` briefly: status becomes `DEGRADED_STORAGE`.
4. `docker compose logs video-capture-service | grep "kafka producer ready"` appears once per topic at start-up, not once per frame.
5. On kind, readiness flips to 503 `degraded` if a capture thread dies (simulate by scaling minio to 0 for long enough to exceed `max_consecutive_failures`), and the pod is not restarted because liveness is separate.

## Follow-ups (not in this branch)

- Layout branch for both Python services (installed package, non-root image, mypy).
- streamlink Python API instead of the CLI.
