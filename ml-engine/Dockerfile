FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY src/main/python ./src/main/python
COPY src/main/resources ./src/main/resources

ENV PYTHONPATH=/app/src/main/python

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
