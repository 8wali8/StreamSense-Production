#!/usr/bin/env python3
"""VOD replay smoke path: verify a replay alias flows through the live stack.

Chat replay is fully offline (fixture-backed), so the chat checks are
deterministic. Frame capture resolves the real Twitch VOD via streamlink and
therefore needs network access; transcript checks additionally need the stack
started with STREAMSENSE_TWITCH_TRANSCRIPT_ENABLED=true.
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
import time

from compose_smoke import graphql, http_json, post_json, run, wait_for_url

REPLAY_SOURCE = "TWITCH_VOD_REPLAY"


def switch_chat_channels(base_url: str, alias: str, timeout: float) -> None:
    try:
        status = post_json(f"{base_url}/api/chat/twitch/channels", {"channels": [alias]}, timeout)
    except RuntimeError as exc:
        if "HTTP 409" in str(exc):
            raise RuntimeError(
                f"{exc}\n"
                "Chat channel switch was rejected because Twitch chat ingestion is disabled.\n"
                "Replay does not need Twitch credentials, but the connector must be enabled:\n"
                "restart the stack with STREAMSENSE_TWITCH_CHAT_ENABLED=true, or rerun with --start-compose."
            ) from exc
        raise
    channels = status.get("channels", [])
    if alias not in channels:
        raise AssertionError(f"Chat switch did not activate {alias}: {status}")
    print(f"OK chat channels switched to {channels}")


def switch_video_channels(base_url: str, alias: str, timeout: float) -> None:
    status = post_json(f"{base_url}/api/video/capture/channels", {"channels": [alias]}, timeout)
    channels = status.get("channels", [])
    if alias not in channels:
        raise AssertionError(f"Video capture switch did not activate {alias}: {status}")
    print(f"OK video capture channels switched to {channels}")


def check_chat_sentiment(base_url: str, alias: str, timeout: float) -> tuple[bool, str]:
    data = graphql(
        base_url,
        "query($streamer: String!, $limit: Int!) {"
        " recentSentiment(streamer: $streamer, limit: $limit) { sentimentEventId label } }",
        {"streamer": alias, "limit": 5},
        timeout,
    )
    events = data.get("recentSentiment", [])
    return bool(events), f"{len(events)} chat sentiment event(s)"


def check_video_frames(base_url: str, alias: str, timeout: float) -> tuple[bool, str]:
    status = http_json(f"{base_url}/api/video/capture/status", timeout)
    for channel in status.get("channelStatuses", []):
        if channel.get("channel") == alias:
            published = channel.get("framesPublished", 0)
            detail = f"state={channel.get('state')} framesPublished={published}"
            if channel.get("lastError"):
                detail += f" lastError={channel['lastError']}"
            return published > 0, detail
    return False, f"channel {alias} not present in capture status"


def check_transcripts(base_url: str, alias: str, timeout: float) -> tuple[bool, str]:
    data = graphql(
        base_url,
        "query($streamer: String!, $limit: Int!) {"
        " recentTranscriptSegments(streamer: $streamer, limit: $limit) { segmentId source } }",
        {"streamer": alias, "limit": 5},
        timeout,
    )
    segments = data.get("recentTranscriptSegments", [])
    if not segments:
        return False, "no transcript segments yet"
    wrong = [s for s in segments if s.get("source") != REPLAY_SOURCE]
    if wrong:
        raise AssertionError(f"Transcript segments without source={REPLAY_SOURCE}: {wrong}")
    return True, f"{len(segments)} transcript segment(s), all source={REPLAY_SOURCE}"


def check_sponsor_detection_sources(base_url: str, alias: str, timeout: float) -> None:
    data = graphql(
        base_url,
        "query($streamer: String!, $limit: Int!) {"
        " sponsorDetections(streamer: $streamer, limit: $limit) { detectionEventId sponsor source } }",
        {"streamer": alias, "limit": 5},
        timeout,
    )
    detections = data.get("sponsorDetections", [])
    if not detections:
        print("OK sponsor detections: none yet (content-dependent, not a failure)")
        return
    wrong = [d for d in detections if d.get("source") != REPLAY_SOURCE]
    if wrong:
        raise AssertionError(f"Sponsor detections without source={REPLAY_SOURCE}: {wrong}")
    print(f"OK sponsor detections: {len(detections)} event(s), all source={REPLAY_SOURCE}")


def poll_checks(checks: dict, deadline_seconds: int, poll_seconds: float) -> None:
    deadline = time.time() + deadline_seconds
    passed: set[str] = set()
    details: dict[str, str] = {}
    while time.time() < deadline and len(passed) < len(checks):
        for name, check in checks.items():
            if name in passed:
                continue
            try:
                ok, detail = check()
            except AssertionError:
                raise
            except Exception as exc:  # noqa: BLE001 - keep polling through transient errors.
                ok, detail = False, f"error: {exc}"
            details[name] = detail
            if ok:
                passed.add(name)
                print(f"OK {name}: {detail}")
        if len(passed) < len(checks):
            pending = {name: details.get(name, "not checked yet") for name in checks if name not in passed}
            print(f"waiting on {pending}")
            time.sleep(poll_seconds)
    failed = [name for name in checks if name not in passed]
    if failed:
        summary = "; ".join(f"{name}: {details.get(name, 'never checked')}" for name in failed)
        raise TimeoutError(f"Replay checks did not pass within {deadline_seconds}s: {summary}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Run VOD replay smoke checks.")
    parser.add_argument("--base-url", default="http://localhost:8080", help="api-gateway base URL")
    parser.add_argument("--alias", default="redbull-testing", help="replay alias to exercise")
    parser.add_argument("--start-compose", action="store_true", help="package and start Compose with replay-friendly env before checks")
    parser.add_argument("--teardown", action="store_true", help="tear Compose down after checks")
    parser.add_argument("--skip-video", action="store_true", help="only verify offline chat replay (no Twitch network needed)")
    parser.add_argument("--expect-transcripts", action="store_true", help="also require transcript segments (stack must run with STREAMSENSE_TWITCH_TRANSCRIPT_ENABLED=true; first run downloads the Whisper model)")
    parser.add_argument("--startup-timeout", type=int, default=360, help="startup wait timeout in seconds")
    parser.add_argument("--deadline-seconds", type=int, default=300, help="how long to wait for replay events")
    parser.add_argument("--poll-seconds", type=float, default=5.0, help="delay between polling rounds")
    parser.add_argument("--timeout", type=float, default=10.0, help="HTTP request timeout in seconds")
    args = parser.parse_args()

    base_url = args.base_url.rstrip("/")
    env = os.environ.copy()
    env["STREAMSENSE_TWITCH_CHAT_ENABLED"] = "true"
    env["STREAMSENSE_TWITCH_VIDEO_ENABLED"] = "true"
    if args.expect_transcripts:
        env["STREAMSENSE_TWITCH_TRANSCRIPT_ENABLED"] = "true"

    try:
        if args.start_compose:
            run(["make", "package"], env=env)
            run(["docker", "compose", "down", "-v", "--remove-orphans"], env=env)
            run(["docker", "compose", "up", "-d", "--build"], env=env)

        wait_for_url(f"{base_url}/actuator/health", args.startup_timeout, args.timeout)
        wait_for_url(f"{base_url}/api/chat/twitch/status", args.startup_timeout, args.timeout)
        if not args.skip_video:
            wait_for_url(f"{base_url}/api/video/capture/status", args.startup_timeout, args.timeout)

        switch_chat_channels(base_url, args.alias, args.timeout)
        if not args.skip_video:
            switch_video_channels(base_url, args.alias, args.timeout)

        checks = {"chat sentiment": lambda: check_chat_sentiment(base_url, args.alias, args.timeout)}
        if not args.skip_video:
            checks["video frames"] = lambda: check_video_frames(base_url, args.alias, args.timeout)
        if args.expect_transcripts:
            checks["transcript segments"] = lambda: check_transcripts(base_url, args.alias, args.timeout)

        poll_checks(checks, args.deadline_seconds, args.poll_seconds)

        if not args.skip_video:
            check_sponsor_detection_sources(base_url, args.alias, args.timeout)

        print("Replay smoke passed")
        return 0
    finally:
        if args.teardown:
            try:
                run(["docker", "compose", "down", "-v", "--remove-orphans"], env=env)
            except subprocess.CalledProcessError as exc:
                print(f"Teardown failed: {exc}", file=sys.stderr)


if __name__ == "__main__":
    raise SystemExit(main())
