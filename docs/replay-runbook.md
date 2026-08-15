# Replay Runbook

This runbook explains the current Twitch VOD replay workflow. Use it when starting the stack, validating the demo, or diagnosing replay failures.

## Purpose

Replay mode lets StreamSense run the live pipeline against a fixed Twitch VOD and fixture chat history. It makes demos and debugging reproducible without depending on an active livestream.

The canonical replay alias is:

- Alias: `redbull-testing`
- Twitch VOD URL: `https://www.twitch.tv/videos/2750461300`
- Twitch VOD id: `2750461300`
- Source marker: `TWITCH_VOD_REPLAY`
- Start offset: `1800` seconds

## How Replay Works

### Video And Transcript

`video-capture-service` reads replay alias environment variables and treats `redbull-testing` as a VOD source instead of a live channel.

Flow:

```text
Twitch VOD URL -> streamlink -> HLS URL -> ffmpeg -ss <vod-offset> -> frame/audio sample
```

Frames are stored in MinIO and published to Kafka as normal frame events. Audio chunks are sent to `ml-engine /ml/transcribe`, then transcript segments are published to Kafka.

Replay-specific fields:

- `streamer = "redbull-testing"`
- `source = "TWITCH_VOD_REPLAY"`
- `channelLogin = "redbull-testing"`
- `twitchStreamId = "2750461300"`
- `streamSessionId = "redbull-testing-2750461300-<session-start-ms>"`
- `videoTimestampMs = <VOD offset in milliseconds>`

### Chat

Live Twitch IRC cannot provide historical VOD chat. `chat-service` therefore replays checked-in fixture chat for `redbull-testing`.

Fixture:

```text
chat-service/src/main/resources/replay/redbull-testing-chat.json
```

The replay scheduler publishes each fixture comment through the existing chat ingest path. Downstream Kafka topics, sentiment consumers, GraphQL subscriptions, and the frontend treat those events like live chat.

### Frontend

For `redbull-testing`, the frontend embeds the Twitch VOD player and still uses the existing channel switch workflow:

1. User enters `redbull-testing`.
2. User clicks `Load Console`.
3. Frontend posts to `/api/chat/twitch/channels` and `/api/video/capture/channels`.
4. Chat, video, transcript, sentiment, and sponsor detections populate through the normal APIs/subscriptions.

## Required Local Setup

Docker Desktop must be running.

`.env.twitch.local` should exist locally and must not be committed. For replay-only `redbull-testing`, Twitch chat credentials are not required by the replay channel, but the env file is still used to enable chat/video/transcript services and optional Twitch tokens.

Useful local toggles:

- `STREAMSENSE_TWITCH_CHAT_ENABLED=true`
- `STREAMSENSE_TWITCH_VIDEO_ENABLED=true`
- `STREAMSENSE_TWITCH_TRANSCRIPT_ENABLED=true`
- `TWITCH_CHANNELS=` can be empty when passing `-Channels redbull-testing`
- `TWITCH_VIDEO_CHANNELS=` can be empty when passing `-Channels redbull-testing`

Do not print or commit OAuth tokens.

## Startup

Full startup with packaging/building:

```powershell
powershell -ExecutionPolicy Bypass -File "tools/start-stack.ps1" -TwitchEnv -Channels redbull-testing
```

Faster startup when Java jars are already packaged:

```powershell
powershell -ExecutionPolicy Bypass -File "tools/start-stack.ps1" -SkipPackage -TwitchEnv -Channels redbull-testing
```

Fastest restart when images are already current:

```powershell
powershell -ExecutionPolicy Bypass -File "tools/start-stack.ps1" -SkipPackage -SkipBuild -TwitchEnv -Channels redbull-testing
```

If Docker reports a missing Linux engine pipe, start Docker Desktop and rerun the command.

## Expected Services

After startup, these URLs should be available:

- Frontend: `http://localhost:3000`
- API Gateway: `http://localhost:8080`
- Kafka UI: `http://localhost:8088`
- Grafana: `http://localhost:3001`
- MinIO: `http://localhost:9001`
- ML Engine: `http://localhost:8000`

Check container state:

```powershell
docker compose ps
```

Expected important states:

- `api-gateway`: healthy
- `chat-service`: healthy
- `video-capture-service`: healthy
- `sentiment-service`: healthy after settling
- `video-service`: healthy after settling
- `kafka`: healthy
- `frontend`: healthy

## Verification Commands

Frontend:

```powershell
Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:3000" -TimeoutSec 15
```

Gateway health:

```powershell
Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:8080/actuator/health" -TimeoutSec 15
```

Chat replay status:

```powershell
Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:8080/api/chat/twitch/status" -TimeoutSec 15
```

Expected chat state:

```json
{"enabled":true,"state":"CONNECTED","channels":["redbull-testing"]}
```

Video replay status:

```powershell
Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:8080/api/video/capture/status" -TimeoutSec 15
```

Expected video state after settling:

```json
{"enabled":true,"state":"CAPTURING","channels":["redbull-testing"]}
```

Recent transcript through the frontend proxy:

```powershell
Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:3000/api/sentiment/transcript/recent?streamer=redbull-testing&limit=2" -TimeoutSec 15
```

GraphQL transcript/sentiment check:

```powershell
$body = @{
  query = 'query($streamer:String!,$limit:Int!){ recentTranscriptSegments(streamer:$streamer,limit:$limit){ segmentId text source twitchStreamId videoTimestampMs } recentTranscriptSentiment(streamer:$streamer,limit:$limit){ sentimentEventId segmentId label score } }'
  variables = @{ streamer = 'redbull-testing'; limit = 2 }
} | ConvertTo-Json -Depth 5

Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:3000/graphql" -Method Post -ContentType "application/json" -Body $body -TimeoutSec 15
```

## Troubleshooting

### Docker Engine Not Available

Symptom:

```text
failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine
```

Fix: start Docker Desktop, wait for the engine, then rerun startup.

### Packaging Is Slow

Java packaging can take a long time because each service is packaged separately. If changed Java services have already been packaged, rerun with `-SkipPackage`.

### Kafka Dependency Error During Startup

Docker Desktop can report a transient Kafka dependency failure while containers are being recreated. The startup helper already runs a second `docker compose up -d`. If services still remain in `Created`, run:

```powershell
docker compose up -d analytics-service sentiment-service video-service kafka-exporter
```

Then recheck:

```powershell
docker compose ps
```

### Chat Connected But No Sentiment

Check that `sentiment-service` is healthy and consuming from Kafka. Sponsor-specific sentiment can be empty if replay text is not relevant to the active sponsor; general chat/transcript sentiment should still flow.

### Transcript Missing In Frontend

First verify raw transcript data exists:

```powershell
Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:3000/api/sentiment/transcript/recent?streamer=redbull-testing&limit=2" -TimeoutSec 15
```

If raw data exists but the UI is blank, inspect frontend runtime errors. The transcript panel should not depend on sponsor-filtered data.

## Current Missing Automation

There is not yet a single replay smoke-check command. The next recommended task is `tools/smoke-replay.ps1`, which should combine the verification commands above and fail with a clear reason if any subsystem is not producing replay data.
