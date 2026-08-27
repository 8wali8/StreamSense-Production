# Current State

This document is the handoff point for contributors and agents that are new to the repository. Read it before changing code.

## Product Summary

StreamSense is a real-time sponsor analytics stack for Twitch streams. It ingests chat, video frames, and streamer transcript segments; enriches them with ML sentiment, sponsor relevance, sponsor detection, segmentation, and transcription; then exposes live results through GraphQL, REST, subscriptions, and a React live console.

## Service Map

- `frontend`: React/Apollo live console served by nginx in Docker Compose.
- `api-gateway`: Spring GraphQL/API gateway, REST proxying, and subscription fan-out from Kafka.
- `chat-service`: Twitch IRC ingestion, manual chat ingest, and fixture-backed Twitch VOD chat replay.
- `video-capture-service`: Python Twitch live/VOD frame capture, audio capture, transcript publishing, and MinIO frame storage.
- `sentiment-service`: Chat and transcript sentiment, sponsor relevance scoring, history persistence, and sentiment Kafka publishing.
- `video-service`: Frame-event consumer, sponsor detection calls, detection persistence, and sponsor-detection Kafka publishing.
- `analytics-service`: Stream metrics aggregation.
- `recommendation-service`: Recommendation summaries over platform signals.
- `ml-engine`: FastAPI ML endpoints for sentiment, sponsor relevance, sponsor detection, segmentation, and transcription.
- `config-server`: Runtime config for Spring services from `config-server/config-repo/*.yml`.
- `eureka-server`: Spring service discovery.

## Current Canonical Demo

The current reliable demo path is the Twitch VOD replay channel:

- Streamer/channel: `redbull-testing`
- Twitch VOD: `https://www.twitch.tv/videos/2750461300`
- VOD id: `2750461300`
- Replay source marker: `TWITCH_VOD_REPLAY`
- Replay start offset: `1800` seconds
- Frontend URL: `http://localhost:3000`
- API Gateway URL: `http://localhost:8080`
- Kafka UI URL: `http://localhost:8088`
- Grafana URL: `http://localhost:3001`

Start it with:

```powershell
powershell -ExecutionPolicy Bypass -File "tools/start-stack.ps1" -TwitchEnv -Channels redbull-testing
```

If Java service jars were already packaged and Docker images only need to be restarted/rebuilt, use:

```powershell
powershell -ExecutionPolicy Bypass -File "tools/start-stack.ps1" -SkipPackage -TwitchEnv -Channels redbull-testing
```

See `docs/replay-runbook.md` for detailed startup, verification, and troubleshooting.

## Recently Completed

- Added replay alias support in `video-capture-service` so a Twitch VOD can be sampled like a live stream.
- Added seek-aware frame and audio capture for replay offsets.
- Added replay metadata on frame/transcript events, including `source`, `twitchStreamId`, `streamSessionId`, and VOD-relative `videoTimestampMs`.
- Added fixture-backed VOD chat replay in `chat-service` for `redbull-testing`.
- Wired replay config through Config Server, Docker Compose, and Kubernetes manifests.
- Added `tools/start-stack.ps1` for repeatable local stack startup with Twitch env loading and runtime channel switching.
- Fixed the frontend transcript panel so raw transcript remains visible after `Load Console` and sponsor-specific transcript rows are highlighted instead of replacing raw transcript.

## Important Implementation Rules

- Keep replay support at the ingestion boundary. Downstream services should keep consuming normal streamer-keyed events.
- Keep raw data and analysis lanes separate:
  - raw chat
  - chat sentiment
  - sponsor chat sentiment
  - raw transcript
  - transcript sentiment
  - sponsor transcript sentiment
  - video sponsor detections
- Sponsor-specific sentiment can be empty even when raw chat/transcript exists. Do not hide raw transcript or raw chat based on sponsor-filtered emptiness.
- Spring service `src/main/resources/application.yml` files are bootstrap-only. Runtime config lives in `config-server/config-repo/*.yml` and Kubernetes mirrors live in `k8s/config/config-server-config-repo.yaml`.
- Python `video-capture-service` does not consume Config Server, so replay aliases used there are mirrored as environment variables in Compose/Kubernetes.
- Do not commit Twitch tokens, `.env.twitch.local`, captured frames, generated `target/**` build output, or local session notes.

## Known Gaps

- Replay verification is still too manual. The next highest-value task is a one-command replay smoke test.
- There is no unified `/api/replay/status` endpoint yet.
- Sponsor relevance profiles are runtime/in-memory and should be persisted or config-seeded for repeatable demos.
- Generated Java build artifacts under some `target/**` paths can show as dirty after builds and should be cleaned up from Git tracking in a hygiene pass.
- The frontend has a working replay path, but it does not yet have a dedicated replay health/timeline panel.

## Best Next Step

Build `tools/smoke-replay.ps1` so one command can answer whether `redbull-testing` replay is working end-to-end.

See `docs/next-work.md` for the recommended backlog order.
