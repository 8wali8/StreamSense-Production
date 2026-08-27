#!/usr/bin/env python3
"""API-level final smoke path for the StreamSense Compose demo."""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request


ROOT_HEALTH_URLS = [
    "http://localhost:8761/actuator/health",
    "http://localhost:8888/actuator/health",
    "http://localhost:8080/actuator/health",
    "http://localhost:8081/actuator/health",
    "http://localhost:8082/actuator/health",
    "http://localhost:8083/actuator/health",
    "http://localhost:8084/actuator/health",
    "http://localhost:8000/ml/health",
    "http://localhost:9090/-/healthy",
    "http://localhost:3001/api/health",
    "http://localhost:9411/health",
]


def run(command: list[str], env: dict[str, str] | None = None) -> None:
    print(f"$ {' '.join(command)}")
    subprocess.run(command, check=True, env=env)


def http_json(url: str, timeout_seconds: float) -> dict:
    with urllib.request.urlopen(url, timeout=timeout_seconds) as response:
        body = response.read().decode("utf-8")
        return json.loads(body) if body else {}


def http_text(url: str, timeout_seconds: float) -> str:
    with urllib.request.urlopen(url, timeout=timeout_seconds) as response:
        return response.read().decode("utf-8", errors="replace")


def post_json(url: str, payload: dict, timeout_seconds: float) -> dict:
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
            body = response.read().decode("utf-8")
            return json.loads(body) if body else {}
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"POST {url} failed with HTTP {exc.code}: {body}") from exc


def wait_for_url(url: str, timeout_seconds: int, request_timeout: float) -> None:
    deadline = time.time() + timeout_seconds
    last_error = None
    while time.time() < deadline:
        try:
            http_text(url, request_timeout)
            print(f"OK {url}")
            return
        except Exception as exc:  # noqa: BLE001 - smoke output should preserve the last failure.
            last_error = exc
            time.sleep(5)
    raise TimeoutError(f"Timed out waiting for {url}: {last_error}")


def graphql(base_url: str, query: str, variables: dict, timeout_seconds: float) -> dict:
    response = post_json(
        f"{base_url.rstrip('/')}/graphql",
        {"query": query, "variables": variables},
        timeout_seconds,
    )
    if response.get("errors"):
        raise RuntimeError(f"GraphQL errors: {json.dumps(response['errors'])}")
    return response.get("data", {})


def assert_non_empty(name: str, values: list) -> None:
    if not values:
        raise AssertionError(f"Expected {name} to be non-empty")
    print(f"OK {name}: {len(values)} item(s)")


def seed_and_assert(base_url: str, timeout_seconds: float, settle_seconds: float) -> None:
    streamer = f"smoke-{int(time.time())}"
    now_ms = int(time.time() * 1000)
    chat = post_json(
        f"{base_url}/api/chat/ingest",
        {
            "streamer": streamer,
            "user": "smoke-user",
            "message": "smoke test loves this stream",
            "timestamp": now_ms,
        },
        timeout_seconds,
    )
    if "eventId" not in chat:
        raise AssertionError(f"Chat ingest did not return eventId: {chat}")
    print(f"OK chat ingest eventId={chat['eventId']}")

    frame = post_json(
        f"{base_url}/api/video/upload-frame",
        {
            "streamer": streamer,
            "frameRef": f"frames/{streamer}.png",
            "frameSequence": 1,
            "capturedAt": now_ms + 1000,
        },
        timeout_seconds,
    )
    if frame.get("status") not in {"accepted", "ACCEPTED", None}:
        raise AssertionError(f"Unexpected frame response: {frame}")
    print("OK video frame accepted")

    time.sleep(settle_seconds)

    data = graphql(
        base_url,
        """
        query Smoke($streamer: String!, $limit: Int!) {
          health
          recentSentiment(streamer: $streamer, limit: $limit) { sentimentEventId label modelVersion }
          sponsorDetections(streamer: $streamer, limit: $limit) { detectionEventId sponsor modelVersion }
          recommendations(streamer: $streamer, limit: $limit) { recommendationId category variantId }
        }
        """,
        {"streamer": streamer, "limit": 5},
        timeout_seconds,
    )
    if data.get("health") != "ok":
        raise AssertionError(f"GraphQL health was not ok: {data.get('health')}")
    print("OK GraphQL health")
    assert_non_empty("recentSentiment", data.get("recentSentiment", []))
    assert_non_empty("sponsorDetections", data.get("sponsorDetections", []))
    assert_non_empty("recommendations", data.get("recommendations", []))

    frontend = urllib.request.urlopen("http://localhost:3000", timeout=timeout_seconds).read().decode("utf-8", errors="replace")
    if "html" not in frontend.lower():
        raise AssertionError("Frontend did not return HTML")
    print("OK frontend HTML")

    zipkin = http_json("http://localhost:9411/api/v2/services", timeout_seconds)
    if not isinstance(zipkin, list):
        raise AssertionError(f"Unexpected Zipkin services response: {zipkin}")
    print(f"OK Zipkin services endpoint: {len(zipkin)} service(s)")


def main() -> int:
    parser = argparse.ArgumentParser(description="Run final API-level Compose smoke checks.")
    parser.add_argument("--base-url", default="http://localhost:8080", help="api-gateway base URL")
    parser.add_argument("--start-compose", action="store_true", help="package and start Compose before checks")
    parser.add_argument("--teardown", action="store_true", help="tear Compose down after checks")
    parser.add_argument("--relaxed-rate-limit", action="store_true", help="start gateway with rate limiting disabled")
    parser.add_argument("--startup-timeout", type=int, default=360, help="startup wait timeout in seconds")
    parser.add_argument("--timeout", type=float, default=10.0, help="HTTP request timeout in seconds")
    parser.add_argument("--settle-seconds", type=float, default=8.0, help="time to wait after seeding")
    args = parser.parse_args()

    env = os.environ.copy()
    if args.relaxed_rate_limit:
        env["STREAMSENSE_GATEWAY_RATE_LIMIT_ENABLED"] = "false"

    try:
        if args.start_compose:
            run(["make", "package"], env=env)
            run(["docker", "compose", "down", "-v", "--remove-orphans"], env=env)
            run(["docker", "compose", "up", "-d", "--build"], env=env)

        for url in ROOT_HEALTH_URLS:
            wait_for_url(url, args.startup_timeout, args.timeout)

        seed_and_assert(args.base_url.rstrip("/"), args.timeout, args.settle_seconds)
        print("Smoke E2E passed")
        return 0
    finally:
        if args.teardown:
            try:
                run(["docker", "compose", "down", "-v", "--remove-orphans"], env=env)
            except subprocess.CalledProcessError as exc:
                print(f"Teardown failed: {exc}", file=sys.stderr)


if __name__ == "__main__":
    raise SystemExit(main())
