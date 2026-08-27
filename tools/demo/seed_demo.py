#!/usr/bin/env python3
"""Seed a small StreamSense demo dataset through the public gateway path."""

from __future__ import annotations

import argparse
import json
import time
import urllib.error
import urllib.request


DEFAULT_MESSAGES = [
    "this stream is great and the chat energy is strong",
    "love this segment and the sponsor placement",
    "the pacing feels solid today",
    "minor lag earlier but the stream recovered well",
]


def post_json(url: str, payload: dict, timeout_seconds: float) -> dict:
    data = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        url,
        data=data,
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


def graphql(base_url: str, query: str, variables: dict, timeout_seconds: float) -> dict:
    response = post_json(
        f"{base_url.rstrip('/')}/graphql",
        {"query": query, "variables": variables},
        timeout_seconds,
    )
    if response.get("errors"):
        raise RuntimeError(f"GraphQL returned errors: {json.dumps(response['errors'])}")
    return response.get("data", {})


def main() -> int:
    parser = argparse.ArgumentParser(description="Seed demo chat and video-frame events.")
    parser.add_argument("--base-url", default="http://localhost:8080", help="api-gateway base URL")
    parser.add_argument("--streamer", default="demo-streamer", help="streamer key to seed")
    parser.add_argument("--timeout", type=float, default=10.0, help="HTTP timeout in seconds")
    parser.add_argument("--settle-seconds", type=float, default=5.0, help="time to wait before history checks")
    args = parser.parse_args()

    base_url = args.base_url.rstrip("/")
    now_ms = int(time.time() * 1000)
    chat_results = []
    frame_results = []

    for index, message in enumerate(DEFAULT_MESSAGES, start=1):
        payload = {
            "streamer": args.streamer,
            "user": f"demo-user-{index}",
            "message": message,
            "timestamp": now_ms + index,
        }
        chat_results.append(post_json(f"{base_url}/api/chat/ingest", payload, args.timeout))

    for sequence in range(1, 4):
        payload = {
            "streamer": args.streamer,
            "frameRef": f"frames/{args.streamer}-{sequence}.png",
            "frameSequence": sequence,
            "capturedAt": now_ms + 1000 + sequence,
        }
        frame_results.append(post_json(f"{base_url}/api/video/upload-frame", payload, args.timeout))

    time.sleep(args.settle_seconds)

    history = graphql(
        base_url,
        """
        query DemoSeedCheck($streamer: String!, $limit: Int!) {
          health
          recentSentiment(streamer: $streamer, limit: $limit) { sentimentEventId label modelVersion }
          sponsorDetections(streamer: $streamer, limit: $limit) { detectionEventId sponsor modelVersion }
          recommendations(streamer: $streamer, limit: $limit) { recommendationId category variantId }
        }
        """,
        {"streamer": args.streamer, "limit": 5},
        args.timeout,
    )

    summary = {
        "streamer": args.streamer,
        "chatEventsAccepted": len(chat_results),
        "framesAccepted": len(frame_results),
        "recentSentimentCount": len(history.get("recentSentiment", [])),
        "sponsorDetectionCount": len(history.get("sponsorDetections", [])),
        "recommendationCount": len(history.get("recommendations", [])),
        "health": history.get("health"),
    }
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
