# ml-engine

Python ML microservice for StreamSense.

## Endpoints

- `GET /ml/health`
- `POST /ml/sentiment`

## Run locally

```bash
uv sync --locked
PYTHONPATH=src/main/python uv run uvicorn app.main:app --reload --port 8000
