# Sponsor Pipeline Contract

## Ownership

- `video-service` owns frame ingest, sponsor inference orchestration, persistence, and sponsor detection history.
- `ml-engine` owns deterministic sponsor inference behind `POST /ml/sponsor`.
- `api-gateway` owns GraphQL sponsor history and live subscription access.

## Event Flow

1. `POST /api/video/upload-frame` accepts a small reference-based frame payload, or `video-capture-service` samples a real Twitch frame and stores it as an artifact.
2. `video-service` publishes `FrameData` to `stream.video.frames`.
3. `video-service` consumes `stream.video.frames`, asks `analytics-service` which deal the channel is in (`GET /api/analytics/streams/{streamer}/current-deal`, cached per channel for `streamsense.services.analytics-service.current-deal-cache-seconds`), and, when that deal has a logo, calls `ml-engine` with the deal's sponsor, ids, and logo refs. A channel without a logo is not sent to `ml-engine` at all.
4. `video-service` persists the resulting `SponsorDetectionEvent`.
5. `video-service` publishes the detection to `stream.sponsor.detections`.
6. `api-gateway` consumes `stream.sponsor.detections` for `onSponsorDetection(streamer)`.
7. `api-gateway` queries `video-service` for `sponsorDetections(streamer, limit)`.

## `FrameData`

```json
{
  "frameId": "string",
  "streamer": "string",
  "frameRef": "string",
  "frameSequence": 1,
  "capturedAt": 1710000000000,
  "source": "TWITCH",
  "channelLogin": "austincs",
  "streamSessionId": "austincs-1710000000000",
  "twitchStreamId": null,
  "videoTimestampMs": 0,
  "artifactContentType": "image/jpeg",
  "artifactSizeBytes": 145231,
  "captureWorkerId": "video-capture-service-1"
}
```

The production metadata fields are optional so existing synthetic frame uploads continue to work.

## What a frame is examined against

The `/ml/sponsor` request (`docs/schemas/ml-sponsor-request.schema.json`) is the `FrameData` fields plus, when the channel's current deal carries a logo, `sponsor` (the name every detection of the frame is stamped with, so analytics keys it under the deal), `dealId`, `logoId` (the first logo's), and `logoRefs` (the logo images as `s3://` URIs, at most two, oldest upload first). The response (`ml-sponsor-response.schema.json`) is the detection plus `outcome`: `DETECTED` (the logo is at the box) or `NOT_DETECTED` (looked, did not find it: confidence 0, zero box). An engine from before outcomes sends none, which reads as `DETECTED`.

Every sampled frame yields one `SponsorDetectionEvent` whatever happened to it, with `outcome`:

| `outcome` | Meaning | `sponsor` | `modelVersion` |
|---|---|---|---|
| `DETECTED` | the logo is in the frame, at the box | the deal's sponsor | the detector's |
| `NOT_DETECTED` | looked for the logo, did not find it | the deal's sponsor | the detector's |
| `NO_LOGO` | nothing to look for: no deal, or a deal without a logo; ml-engine was not asked | the deal's sponsor, or `UNKNOWN` without a deal | `no-logo` |
| `UNAVAILABLE` | could not be examined: ml-engine failed, or the deal could not be looked up | `UNKNOWN` | `fallback` |

`dealId` and `logoId` say what the frame was examined against. An event from before outcomes (null) reads as `UNAVAILABLE` when its model version is `fallback` and `DETECTED` otherwise. analytics-service tallies each frame accordingly: only a `DETECTED` frame can be accepted (on-screen time) or low confidence; `DETECTED` and `NOT_DETECTED` frames are examined; `NO_LOGO` and `UNAVAILABLE` are counted apart, and the session report's `onScreenTracking` is built from those totals (see `sessions.md`). The gateway's timeline builds on-screen segments from `DETECTED` frames only.

## `SponsorDetectionEvent`

```json
{
  "detectionEventId": "string",
  "sourceFrameId": "string",
  "streamer": "string",
  "frameRef": "string",
  "frameSequence": 1,
  "capturedAt": 1710000000000,
  "processedAt": 1710000000500,
  "sponsor": "Nike",
  "confidence": 0.91,
  "modelVersion": "stub-v1",
  "x": 0.12,
  "y": 0.18,
  "width": 0.31,
  "height": 0.24,
  "source": "TWITCH",
  "channelLogin": "austincs",
  "streamSessionId": "austincs-1710000000000",
  "twitchStreamId": null,
  "videoTimestampMs": 0,
  "outcome": "DETECTED",
  "dealId": 3,
  "logoId": 7
}
```

When Phase 2 video capture is enabled, `frameRef` points to a real stored frame artifact such as `s3://streamsense-frames/twitch/austincs/session/frame.jpg`, and `ml-engine` validates the artifact before returning the detection.

## Fallback Contract

When sponsor inference degrades, `video-service` still persists and publishes a real detection event:

```json
{
  "sponsor": "UNKNOWN",
  "confidence": 0.0,
  "modelVersion": "fallback",
  "x": 0.0,
  "y": 0.0,
  "width": 0.0,
  "height": 0.0
}
```

This keeps degraded behavior visible in GraphQL, the frontend, logs, and metrics instead of silently dropping the frame. The event's `outcome` is `UNAVAILABLE`, and the session report says how long tracking was unavailable rather than showing placeholder numbers. `streamsense_sponsor_outcomes_total{outcome}` on video-service counts every frame by outcome.
