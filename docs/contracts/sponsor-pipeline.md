# Sponsor Pipeline Contract

## Ownership

- `video-service` owns frame ingest, sponsor inference orchestration, persistence, and sponsor detection history.
- `ml-engine` owns deterministic sponsor inference behind `POST /ml/sponsor`.
- `api-gateway` owns GraphQL sponsor history and live subscription access.

## Event Flow

1. `POST /api/video/upload-frame` accepts a small reference-based frame payload.
2. `video-service` publishes `FrameData` to `stream.video.frames`.
3. `video-service` consumes `stream.video.frames` and calls `ml-engine`.
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
  "capturedAt": 1710000000000
}
```

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
  "height": 0.24
}
```

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

This keeps degraded behavior visible in GraphQL, the frontend, logs, and metrics instead of silently dropping the frame.
