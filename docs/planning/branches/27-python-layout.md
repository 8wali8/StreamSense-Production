# hardening/27-python-layout

Item 27 of `docs/planning/production-hardening-followups.md` (follow-ups from branches 02c, 06a, 06b): the two Python services are real packages in the `src/` layout, installed into their virtualenvs by uv, and nothing sets `PYTHONPATH` any more. Stacked on `hardening/26-archunit-and-coverage`.

## What was wrong

Both services kept their code in a Maven-shaped `src/main/python/app/` tree and tests in `src/test/python/`, with `[tool.uv] package = false`, pytest's `pythonpath` pointing at the source directory, and `PYTHONPATH=/app/src/main/python` baked into the images. Everything imported a top-level package called `app`, which is the name FastAPI also uses for the application object, so `app.main:app` was the entry point and monkeypatch targets read `"app.capture_loop.FrameSampler"`. Whether an import worked depended on the current directory or an environment variable, not on what was installed. ml-engine also shipped a `src/main/resources/application.yml` (a Spring-style leftover: `server.port`, `ml-engine.model-version`) that nothing read.

## What changed

- **Layout** (all moves with `git mv`, so history follows):

  | Before | After |
  |---|---|
  | `ml-engine/src/main/python/app/` | `ml-engine/src/ml_engine/` |
  | `ml-engine/src/test/python/` | `ml-engine/tests/` |
  | `ml-engine/src/main/resources/application.yml` | deleted (unused) |
  | `video-capture-service/src/main/python/app/` | `video-capture-service/src/video_capture_service/` |
  | `video-capture-service/src/test/python/` | `video-capture-service/tests/` |

  Imports become `from ml_engine.settings import …` / `from video_capture_service.capture_loop import …` (80 import lines and 14 monkeypatch targets rewritten); the contract tests find `docs/schemas` two levels up instead of four.
- **Packaging**: each `pyproject.toml` gains a `[build-system]` (hatchling) and `[tool.hatch.build.targets.wheel] packages = ["src/<package>"]`; `package = false` is gone, so `uv sync` installs the project into the venv (editable locally). `uv.lock` was regenerated for both (the project entry now records it as a package; no dependency versions changed). pytest's `testpaths` is `tests` with no `pythonpath`; ruff's per-file ignores and isort's first-party list, and mypy's `files`, follow the new paths. `uv build --wheel` produces `streamsense_ml_engine-0.1.0` and `streamsense_video_capture_service-0.1.0` whose only top-level entry is the package.
- **Images**: `uv sync --locked --no-dev --no-install-project` first (dependency layer still cached across code changes), then `COPY src ./src` and `uv sync --locked --no-dev --no-editable`, which installs the package into `/app/.venv/lib/python3.11/site-packages`. `PYTHONPATH` is removed; `CMD` is `uvicorn ml_engine.main:app` / `uvicorn video_capture_service.main:app`. ml-engine no longer copies the resources directory.
- **CI, pre-commit, docs**: the four ruff commands in `ci.yml` run on `src tests`; the pre-commit ruff hooks cover `(src|tests)/`; CLAUDE.md (commands, the layout rule, the settings path), AGENTS.md, CONTRIBUTING.md, and `ml-engine/readme.md` (`uv run uvicorn ml_engine.main:app --reload --port 8000`, no `PYTHONPATH=`) describe the new shape.

## Deliberately left alone

- Distribution names stay `streamsense-ml-engine` and `streamsense-video-capture-service`; only the import packages were named (`ml_engine`, `video_capture_service`).
- No `[project.scripts]` entry point: the images and the readme run uvicorn with the module path, and a console script would only add a second way to start the same app.
- Compose and Kubernetes manifests did not need a change: neither set `PYTHONPATH` or a command; both use the image `CMD`.
- The Java-side `src/main/java` layout is Maven's own and is not touched.

## Verification

| Check | Command | Result |
|---|---|---|
| Relock and install | `uv lock`, `uv sync --locked` (uv 0.12.9, `python:3.11.16-slim`) | ml-engine resolved 79 packages, video-capture-service 59; both install the project |
| Import without the source directory | `cd / && .venv/bin/python -c "import ml_engine.main"` (and `video_capture_service.main`) | both import from the installed package |
| Lint, format, types | `uv run ruff check src tests`, `uv run ruff format --check src tests`, `uv run mypy` | all clean for both (ruff's isort fixes after the rename applied: 5 and 2 files) |
| Tests | `uv run pytest` | ml-engine 72 passed, 1 skipped; video-capture-service 48 passed (same counts as branch 26) |
| Wheel contents | `uv build --wheel` | one top-level package each plus `dist-info` |
| Images build | `docker build ./ml-engine`, `docker build ./video-capture-service` | both succeed; hadolint clean; Trivy misconfiguration gate on the tree exit 0 |
| Images run without `PYTHONPATH` | `docker inspect` env, `python -c "import ml_engine; print(ml_engine.__file__)"` in the image | 0 `PYTHONPATH` entries; package at `/app/.venv/lib/python3.11/site-packages/<package>/`; `/app/src` not on `sys.path` |
| Read-only run (branch 24's rule) | `docker run --read-only --tmpfs /tmp <image>` and probe | ml-engine `/ml/live` and `/ml/ready` ready in 4 s (stub backends), video-capture-service `/live` and `/ready` in 2 s, uid 10001, no read-only or import errors |
| Compose and workflow | `docker compose config -q`, `actionlint` | clean |

## Manual checks for the reviewer

1. `cd ml-engine && uv sync --locked && uv run pytest` then `uv run uvicorn ml_engine.main:app --port 8000` from the service directory: `/ml/live` answers; the old `PYTHONPATH=src/main/python` prefix is no longer needed.
2. `make up` (or `docker compose up --build ml-engine video-capture-service`): both containers report ready and `docker compose exec ml-engine env | grep PYTHONPATH` prints nothing.
3. `git log --follow ml-engine/src/ml_engine/settings.py` continues past the move.
4. `make smoke-e2e` against the running stack: the sentiment path through ml-engine is unchanged.

## Follow-ups

- None specific to this branch; branch 28 (ML stack upgrade) builds on the same `pyproject.toml`.
