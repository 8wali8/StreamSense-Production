# Transcript Pipeline Contract

Phase 2.5 adds streamer speech transcript data without mixing it into chat sentiment.

## Topics

- `stream.transcript.segments`: non-empty transcript text from Twitch audio chunks.
- `stream.transcript.segments.dlt`: transcript segment dead-letter topic.
- `stream.transcript.sentiment.events`: sentiment computed from transcript text only.

## Storage

- `transcript_segments` stores the long-lived transcript text and capture metadata.
- `transcript_sentiment_events` stores transcript-only sentiment output.
- Raw WAV chunks are temporary capture artifacts and are deleted after transcription succeeds or fails.

## ML Boundary

- `POST /ml/transcribe` accepts multipart WAV uploads.
- Local `faster-whisper` defaults to `small.en`, `int8`, CPU.
- Silence returns an empty `text`; capture publishes only non-empty transcript segments.
- Transcript sentiment currently reuses `/ml/sentiment` and remains separate from chat sentiment topics, tables, GraphQL fields, and frontend panels.
