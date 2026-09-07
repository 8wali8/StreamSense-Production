# hardening/02c-python-packaging

Priority 2 (part c) from `docs/planning/production-hardening.md`: the two Python services get a `pyproject.toml`, a committed `uv.lock`, a separate dev dependency group, and Docker images and CI that install from the lock. Stacked on `hardening/02b-image-pinning` because both rewrite the Python Dockerfiles (the plan listed `main` as the base; stacking avoids a guaranteed conflict).

## What changed

**`pyproject.toml` per service** replaces `requirements.txt`. Runtime dependencies carry compatible ranges (`fastapi>=0.115,<1`, `boto3>=1.34,<2`, and so on); the versions that were already exact (`torch==2.2.2+cpu`, `torchvision==0.17.2+cpu`, `transformers==4.40.2`, `sentence-transformers==2.6.1`) stay exact. `kafka-python` is pinned to `>=3.0,<4`: the library resumed releases in 2025 and its 3.x line has breaking changes, so an unpinned install could have silently crossed that boundary. CPU-only PyTorch comes from the PyTorch index through `[tool.uv.index]` with `explicit = true` and `[tool.uv.sources]`, replacing the `--extra-index-url` line. `requires-python = ">=3.11,<3.12"` matches CI and the image.

**`uv.lock` per service**, resolved with uv 0.12.9 for `sys_platform == 'linux'` and `'win32'` (the platforms the services are built and developed on). 67 packages for ml-engine, 49 for video-capture-service. Notable resolved versions: fastapi 0.141.1, uvicorn 0.52.4, kafka-python 3.0.11, streamlink 8.5.0, faster-whisper 1.2.1, numpy 1.26.4.

**Dev group**: `pytest`, `ruff==0.16.3` (the version CI already pinned), and for ml-engine `httpx` (only `TestClient` needs it). `uv sync --locked` installs it for developers and CI; the images use `--no-dev`, so `pytest` is no longer shipped in production images.

**Dockerfiles** copy the `uv` binary from its digest-pinned release image, install the lock before copying source (dependency layer cached across code changes), use a BuildKit cache mount for the uv cache, and put `/app/.venv/bin` on `PATH`. The entrypoints are unchanged. A `.dockerignore` keeps `.venv`, caches, and `src/test` out of the build context.

**CI** uses `astral-sh/setup-uv` (SHA-pinned, uv 0.12.9, Python 3.11, lock-keyed cache) and runs `uv sync --locked`, `uv run ruff check`, `uv run pytest` for both services. `uv sync --locked` fails if the lock is stale, so a `pyproject.toml` edit without a lock update cannot pass CI. video-capture-service is now linted in CI; it was not before.

**Ruff config** moved from `ml-engine/ruff.toml` into `[tool.ruff]` in each `pyproject.toml` so the two services lint identically: `line-length = 120` (the code base already uses long lines; ruff's default 88 only affected import wrapping) and `known-first-party = ["app"]` so imports of `app.*` sort as first-party. video-capture-service gets two per-file `BLE001` ignores (blind `except Exception` in `capture_loop.py` and `storage.py`) that branch 06b removes by introducing typed exceptions, mirroring the ignore ml-engine already carried for `segmentation.py`.

**Import-order fixes in seven ml-engine test files** (`ruff --fix`, I001 only): with `app` declared first-party, its imports move into their own block after third-party imports. No logic changes.

**Two one-line code changes** so video-capture-service lints clean: `status.py` uses `min(...)` instead of `sorted(...)[0]` (ruff FURB192), and `metrics.py` loses one blank line after its import block (ruff I001 auto-fix).

**pytest** reads `pythonpath = ["src/main/python"]` from `pyproject.toml`, so `uv run pytest` works without `PYTHONPATH`. The makefile, CLAUDE.md, AGENTS.md, `ml-engine/readme.md`, and `docs/howtorun.md` describe the uv commands.

## Deliberately left alone

- The `src/main/python/app` layout and `PYTHONPATH` in the images. Moving to a `src/<package>` layout with an installed package is a separate change (`package = false` in `[tool.uv]` records the decision); it belongs with the lifecycle rework in 06a and 06b.
- No `USER` in the Python Dockerfiles yet. ml-engine writes model caches to `/models/*` volumes owned by root; switching users needs the volume ownership handled at the same time (06a).
- No mypy yet. Type checking is its own change with its own ratchet plan.
- ruff's rule set is still the default (which in 0.16 includes import sorting, blind-except, and refurb checks); an explicit `select` list comes later.

## Verification

Container runs copy the tree and normalise file modes first, because a Windows bind mount presents every file as executable and trips ruff's `EXE002`; in git all 47 Python files are mode 100644, which is what CI sees.

| Check | Command | Result |
|---|---|---|
| Lock resolves | `uv lock` in `python:3.11.16-slim` with uv 0.12.9 | ml-engine: 67 packages; video-capture-service: 49 packages |
| video-capture-service | `uv sync --locked && uv run ruff check src/main/python src/test/python && uv run pytest` | ruff: all checks passed; 23 passed |
| ml-engine | same, full suite including the torch and transformers backends | ruff: all checks passed; 45 passed, 1 skipped (the skip is pre-existing) |
| video-capture image | `docker build video-capture-service`, then import `kafka`, `streamlink`, `boto3`, `fastapi` inside it and confirm `pytest` is absent | builds (2525c405…); `import kafka, streamlink, boto3, fastapi` succeeds with kafka-python 3.0.11; `import pytest` fails with ModuleNotFoundError |
| Workflow lint | `actionlint -no-color .github/workflows/ci.yml` | no findings |

The ml-engine image (CPU torch, roughly 1 GB of wheels) was not rebuilt locally; CI's smoke job builds it with the new Dockerfile.

## Manual checks for the reviewer

1. `make up` builds both Python images with the uv Dockerfiles; `docker compose logs ml-engine video-capture-service` show the same startup lines as before.
2. `make replay-smoke` passes (frames and transcripts still flow, which exercises kafka-python 3.x and streamlink 8.x end to end).
3. `docker compose exec ml-engine python -c "import pytest"` fails with `ModuleNotFoundError`, proving the dev group is not in the image.
4. In a checkout with uv installed: `cd ml-engine && uv sync --locked && uv run pytest` and the same in `video-capture-service/`.
5. Change a dependency range in a `pyproject.toml` without running `uv lock` and push: CI's `uv sync --locked` must fail.

## Follow-ups (not in this branch)

- 06a and 06b: `src/<package>` layout, `create_app()` plus lifespan, non-root `USER`, typed exceptions (removing the `BLE001` ignores), health and readiness endpoints, mypy.
- Branch 14: Renovate keeps `uv.lock`, the uv image digest, and the setup-uv SHA current.
