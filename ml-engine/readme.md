# ml-engine

Python ML microservice for StreamSense.

## Endpoints

- `GET /ml/live` (process is up), `GET /ml/ready` (503 until the backends are constructed), `GET /ml/health` (legacy: ok plus a `ready` flag), `GET /ml/info` (backends, models, loaded state, version), `GET /metrics` (Prometheus)
- `POST /ml/sentiment`, `POST /ml/relevance`, `POST /ml/sponsor`, `POST /ml/segment`, `POST /ml/transcribe`

The app is built by `create_app()` in `src/ml_engine/main.py`; settings come from `src/ml_engine/settings.py` (pydantic-settings, one class per env prefix) and every model backend lives in the `BackendRegistry` created in the lifespan. Set `STREAMSENSE_<BACKEND>_PRELOAD=true` to load a model at start-up instead of on first request.

## Run locally

```bash
uv sync --locked
uv run uvicorn ml_engine.main:app --reload --port 8000
