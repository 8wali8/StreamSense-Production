"""Prometheus metrics for ml-engine: HTTP request metrics via middleware plus inference histograms."""

from __future__ import annotations

import time
from collections.abc import Iterator
from contextlib import contextmanager

from fastapi import FastAPI, Request, Response
from prometheus_client import CONTENT_TYPE_LATEST, Counter, Histogram, generate_latest
from starlette.routing import Match

http_request_duration_seconds = Histogram(
    "http_request_duration_seconds",
    "HTTP request latency by method, route template, and status",
    labelnames=("method", "handler", "status"),
    buckets=(0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0, 30.0),
)

http_requests_total = Counter(
    "http_requests_total",
    "HTTP requests by method, route template, and status",
    labelnames=("method", "handler", "status"),
)

inference_seconds = Histogram(
    "streamsense_ml_inference_seconds",
    "Wall time of one inference call, by backend",
    labelnames=("backend",),
    buckets=(0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0, 30.0),
)

inference_failures = Counter(
    "streamsense_ml_inference_failures_total",
    "Inference calls that raised, by backend and error class",
    labelnames=("backend", "error"),
)

forced_failures = Counter(
    "streamsense_ml_forced_failures_total",
    "Requests rejected because ML_ENGINE_FORCE_FAILURE is on",
    labelnames=("endpoint",),
)

UNINSTRUMENTED_PATHS = frozenset({"/metrics", "/ml/live", "/ml/ready", "/ml/health"})


@contextmanager
def timed(backend: str) -> Iterator[None]:
    started = time.perf_counter()
    try:
        yield
    except Exception as exc:
        inference_failures.labels(backend=backend, error=type(exc).__name__).inc()
        raise
    finally:
        inference_seconds.labels(backend=backend).observe(time.perf_counter() - started)


def _route_template(request: Request) -> str:
    """The matched route's path template, so /ml/sentiment is one series, not one per request."""
    for route in request.app.routes:
        match, _ = route.matches(request.scope)
        if match == Match.FULL:
            return getattr(route, "path", request.url.path)
    return "unmatched"


def install_http_metrics(app: FastAPI) -> None:
    @app.middleware("http")
    async def record_http_metrics(request: Request, call_next):
        if request.url.path in UNINSTRUMENTED_PATHS:
            return await call_next(request)
        started = time.perf_counter()
        status = "500"
        try:
            response = await call_next(request)
            status = str(response.status_code)
            return response
        finally:
            handler = _route_template(request)
            elapsed = time.perf_counter() - started
            http_request_duration_seconds.labels(request.method, handler, status).observe(elapsed)
            http_requests_total.labels(request.method, handler, status).inc()

    @app.get("/metrics", include_in_schema=False)
    def metrics_endpoint() -> Response:
        return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)
