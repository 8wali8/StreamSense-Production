# StreamSense event and API schemas

One JSON Schema (draft 2020-12) per Kafka event, plus the two ml-engine sentiment payloads, named `<subject>.schema.json`. The other ml-engine payloads (sponsor relevance, sponsor detection, segmentation, transcription) are defined by the Pydantic models in `ml-engine/src/main/python/app/models.py` and are not schema-checked yet; adding them is listed as a follow-up in the branch record.
These are enforced, not documentation: every producer and consumer test validates a serialised
sample against its schema (networknt `json-schema-validator` in Java, `jsonschema` in Python), and
`tools/schema/check_compat.py` fails CI when a change would break existing consumers.

| Schema | Kafka topic / endpoint | Producer | Consumers |
|---|---|---|---|
| `chat-message-event.schema.json` | `stream.chat.messages` (key = streamer) | chat-service | sentiment-service, analytics-service, api-gateway |
| `sentiment-analysis-event.schema.json` | `stream.sentiment.events` | sentiment-service | analytics-service, api-gateway |
| `transcript-segment-event.schema.json` | `stream.transcript.segments` (key = streamSessionId) | video-capture-service | sentiment-service, api-gateway |
| `transcript-sentiment-event.schema.json` | `stream.transcript.sentiment.events` | sentiment-service | analytics-service, api-gateway |
| `frame-data.schema.json` | `stream.video.frames` (key = streamSessionId from video-capture-service; the `POST /api/video/upload-frame` path in video-service keys by streamer and sets no session id) | video-capture-service, video-service (upload path) | video-service |
| `sponsor-detection-event.schema.json` | `stream.sponsor.detections` | video-service | analytics-service, api-gateway |
| `ml-sentiment-request.schema.json` | `POST /ml/sentiment` request | sentiment-service | ml-engine |
| `ml-sentiment-response.schema.json` | `POST /ml/sentiment` response | ml-engine | sentiment-service |

## Rules

- Timestamps are epoch milliseconds (UTC) as integers.
- `additionalProperties` is `false` everywhere: an unknown field is a contract change, not noise.
- Backward compatible changes only: add optional properties, widen a type to allow `null`, add enum
  values. Never add a required property, remove a property, narrow a type, or drop an enum value.
  If a breaking change is unavoidable, publish a new topic (`stream.<name>.v2`) with a new schema.
- The optional session fields (`source`, `channelLogin`, `streamSessionId`, `twitchStreamId`) are
  the same on every event that carries them; analytics keys its buckets by `streamSessionId` when
  present and by `streamer` otherwise.
- Keep the Java event class, the Python dataclass, the schema, and the contract test in the same
  commit.
