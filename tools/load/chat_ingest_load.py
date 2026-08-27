#!/usr/bin/env python3

from __future__ import annotations

import argparse
import concurrent.futures
import json
import math
import statistics
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any


def percentile(values: list[float], fraction: float) -> float | None:
    if not values:
        return None
    if len(values) == 1:
        return values[0]

    ordered = sorted(values)
    index = (len(ordered) - 1) * fraction
    lower = math.floor(index)
    upper = math.ceil(index)
    if lower == upper:
        return ordered[int(index)]

    lower_value = ordered[lower]
    upper_value = ordered[upper]
    weight = index - lower
    return lower_value + (upper_value - lower_value) * weight


def summarize(values: list[float]) -> dict[str, float | int | None]:
    return {
        "count": len(values),
        "min_ms": round(min(values), 2) if values else None,
        "max_ms": round(max(values), 2) if values else None,
        "mean_ms": round(statistics.fmean(values), 2) if values else None,
        "p50_ms": round(percentile(values, 0.50), 2) if values else None,
        "p95_ms": round(percentile(values, 0.95), 2) if values else None,
    }


def post_json(url: str, payload: dict[str, Any], timeout_seconds: float) -> tuple[int, dict[str, Any] | None, str | None]:
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )

    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            body = response.read().decode("utf-8")
            return response.status, json.loads(body) if body else None, None
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8") if exc.fp is not None else ""
        return exc.code, json.loads(body) if body else None, body or str(exc)
    except urllib.error.URLError as exc:
        return 0, None, str(exc)


def get_json(url: str, timeout_seconds: float) -> tuple[int, Any, str | None]:
    request = urllib.request.Request(url, headers={"Accept": "application/json"}, method="GET")

    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            body = response.read().decode("utf-8")
            return response.status, json.loads(body) if body else None, None
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8") if exc.fp is not None else ""
        return exc.code, json.loads(body) if body else None, body or str(exc)
    except urllib.error.URLError as exc:
        return 0, None, str(exc)


def run_request(base_url: str, timeout_seconds: float, sequence: int, streamer_count: int, message_prefix: str) -> dict[str, Any]:
    timestamp_ms = int(time.time() * 1000)
    streamer = f"load-streamer-{sequence % streamer_count}"
    payload = {
        "streamer": streamer,
        "user": f"load-user-{sequence % 10}",
        "message": f"{message_prefix} #{sequence}",
        "timestamp": timestamp_ms,
    }

    started = time.perf_counter()
    status_code, body, error = post_json(f"{base_url}/api/chat/ingest", payload, timeout_seconds)
    finished = time.perf_counter()
    request_latency_ms = (finished - started) * 1000

    return {
        "sequence": sequence,
        "streamer": streamer,
        "payload": payload,
        "request_latency_ms": round(request_latency_ms, 2),
        "status_code": status_code,
        "event_id": body.get("eventId") if isinstance(body, dict) else None,
        "error": error,
    }


def fetch_sentiment_history(base_url: str, timeout_seconds: float, ingests: list[dict[str, Any]]) -> dict[str, Any]:
    by_streamer: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for ingest in ingests:
        by_streamer[ingest["streamer"]].append(ingest)

    matched_latencies: list[float] = []
    unmatched_event_ids: list[str] = []
    streamer_results: dict[str, dict[str, Any]] = {}

    for streamer, streamer_ingests in by_streamer.items():
        event_lookup = {ingest["event_id"]: ingest for ingest in streamer_ingests if ingest.get("event_id")}
        limit = min(max(len(streamer_ingests) + 5, 5), 100)
        encoded_streamer = urllib.parse.quote(streamer)
        status_code, body, error = get_json(
            f"{base_url}/api/sentiment/recent?streamer={encoded_streamer}&limit={limit}",
            timeout_seconds,
        )

        history = body if isinstance(body, list) else []
        matches = 0

        for event in history:
            source_event_id = event.get("sourceEventId")
            ingest = event_lookup.get(source_event_id)
            if ingest is None:
                continue

            matches += 1
            matched_latencies.append(float(event["processedAt"] - ingest["payload"]["timestamp"]))

        for event_id in event_lookup:
            if not any(item.get("sourceEventId") == event_id for item in history):
                unmatched_event_ids.append(event_id)

        streamer_results[streamer] = {
            "status_code": status_code,
            "history_count": len(history),
            "matched_count": matches,
            "error": error,
        }

    return {
        "summary": summarize(matched_latencies),
        "matched_event_count": len(matched_latencies),
        "unmatched_event_count": len(unmatched_event_ids),
        "unmatched_event_ids": unmatched_event_ids,
        "streamers": streamer_results,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate paced chat ingest load through the StreamSense gateway.")
    parser.add_argument("--base-url", default="http://localhost:8080", help="Gateway base URL")
    parser.add_argument("--rate", type=float, default=2.0, help="Requests per second")
    parser.add_argument("--duration", type=int, default=30, help="Run duration in seconds")
    parser.add_argument("--streamers", type=int, default=3, help="Distinct streamer keys to cycle through")
    parser.add_argument("--workers", type=int, default=8, help="Concurrent worker threads")
    parser.add_argument("--timeout-seconds", type=float, default=5.0, help="Per-request timeout")
    parser.add_argument("--settle-seconds", type=int, default=8, help="Wait after load before checking sentiment history")
    parser.add_argument("--message-prefix", default="week11-load", help="Chat message prefix")
    parser.add_argument("--output", help="Optional JSON output file")
    args = parser.parse_args()

    total_requests = max(int(args.rate * args.duration), 1)
    interval_seconds = 1.0 / args.rate if args.rate > 0 else 0.0
    request_results: list[dict[str, Any]] = []
    lock = threading.Lock()

    started_wall = time.time()
    started_perf = time.perf_counter()

    with concurrent.futures.ThreadPoolExecutor(max_workers=max(args.workers, 1)) as executor:
        futures: list[concurrent.futures.Future[dict[str, Any]]] = []

        for sequence in range(total_requests):
            target_perf = started_perf + (sequence * interval_seconds)
            sleep_for = target_perf - time.perf_counter()
            if sleep_for > 0:
                time.sleep(sleep_for)

            future = executor.submit(
                run_request,
                args.base_url.rstrip("/"),
                args.timeout_seconds,
                sequence,
                max(args.streamers, 1),
                args.message_prefix,
            )
            futures.append(future)

        for future in concurrent.futures.as_completed(futures):
            result = future.result()
            with lock:
                request_results.append(result)

    finished_wall = time.time()
    elapsed_seconds = finished_wall - started_wall

    request_latencies = [item["request_latency_ms"] for item in request_results]
    success_results = [item for item in request_results if 200 <= item["status_code"] < 300 and item.get("event_id")]
    status_counts = Counter(str(item["status_code"]) for item in request_results)

    if args.settle_seconds > 0:
        time.sleep(args.settle_seconds)

    sentiment_history = fetch_sentiment_history(args.base_url.rstrip("/"), args.timeout_seconds, success_results)

    report = {
        "scenario": "chat-ingest",
        "base_url": args.base_url.rstrip("/"),
        "started_at_epoch_ms": int(started_wall * 1000),
        "finished_at_epoch_ms": int(finished_wall * 1000),
        "duration_seconds": args.duration,
        "configured_rate_per_second": args.rate,
        "requested_count": total_requests,
        "streamer_count": args.streamers,
        "workers": args.workers,
        "settle_seconds": args.settle_seconds,
        "results": {
            "request_summary": {
                **summarize(request_latencies),
                "successful_count": len(success_results),
                "failed_count": len(request_results) - len(success_results),
                "achieved_requests_per_second": round(len(request_results) / elapsed_seconds, 2) if elapsed_seconds else None,
                "status_codes": dict(sorted(status_counts.items())),
            },
            "sentiment_end_to_end_summary": sentiment_history,
        },
    }

    if args.output:
        output_path = Path(args.output)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")

    print(json.dumps(report, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
