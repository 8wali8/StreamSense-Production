# ml-engine

Python ML microservice for StreamSense.

## Endpoints

- `GET /ml/health`
- `POST /ml/sentiment`

## Run locally

```bash
pip install -r requirements.txt
PYTHONPATH=src/main/python uvicorn app.main:app --reload --port 8000
