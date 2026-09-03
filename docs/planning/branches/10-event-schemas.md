# hardening/10-event-schemas

Priority 9 from `docs/planning/production-hardening.md`: the Kafka event contracts are enforced, not just documented. Stacked on `hardening/09-problem-details`.

## What was wrong

- `docs/schemas/` mixed two naming styles (`chat-message-event.json` next to `frame-data.schema.json`), two dialects (draft-07 and 2020-12), and two `$id` hosts. Nothing validated against them: the three "contract tests" compared property *names* by hand and would have accepted any type.
- The transcript schemas were not usable as validators: `transcript-segment-event` pinned `source` to `const "TWITCH"` although VOD replay sends `TWITCH_VOD_REPLAY`, `label` on `transcript-sentiment-event` had no type, and neither forbade unknown properties. sentiment-service also emits six sponsor-relevance fields on transcript sentiment events that the schema did not know about.
- analytics-service reads `source`, `channelLogin`, `streamSessionId`, and `twitchStreamId` from chat and sentiment events to key its buckets by capture session, but chat-service never sent them, sentiment-service dropped them, and the schemas did not list them. analytics also carried an `ingestedAt` field no producer sends, and a `fallback` flag that video-service never set.

## What changed

**Schemas** (`docs/schemas/`): every file is `<subject>.schema.json`, draft 2020-12, `$id` under `https://streamsense.dev/schemas/`, `additionalProperties: false`. `chat-message-event` and `sentiment-analysis-event` gain the four optional session fields; `transcript-segment-event` types `source` as a string; `transcript-sentiment-event` types `label` and gains the optional relevance fields; `sponsor-detection-event` gains optional `fallback`. The README is now an index (schema, topic, producer, consumers) plus the compatibility rules.

**Contract tests that validate.** `com.networknt:json-schema-validator` (1.5.9, pinned in the parent POM, test scope) backs an `events/EventContractTest` in chat, sentiment, video, analytics, and the gateway. Producers serialise a real event and assert zero violations, plus one negative case each (missing message, unknown label, confidence above 1) so a silently permissive validator would be noticed. Consumers validate a full sample against the schema and then deserialise it with the mapper they actually use (`JacksonUtils.enhancedObjectMapper()` for Spring Kafka's `JsonDeserializer`, a Boot-style mapper for analytics' string consumer). The three name-comparison tests are deleted. On the Python side `jsonschema` (4.26, dev group) backs `test_event_contracts.py` in video-capture-service (every schema meta-validates as draft 2020-12; `FrameEvent` and a replayed `TranscriptSegmentEvent` conform) and `test_ml_contracts.py` in ml-engine (`SentimentRequest`/`SentimentResponse` against the `ml-sentiment-*` schemas).

**Session fields end to end.** `ChatMessageEvent` in chat-service, sentiment-service, and the gateway carries `source`, `channelLogin`, `streamSessionId`, `twitchStreamId`. chat-service sets `source` = `TWITCH` (IRC), `TWITCH_VOD_REPLAY` (replay, with `twitchStreamId` = the VOD id), or `MANUAL` (`POST /api/chat/ingest`), and `channelLogin` in all three. `SentimentService.buildSentimentEvent` (now package-private static, unit-tested) copies them onto `SentimentAnalysisEvent`, which gains the same fields in sentiment-service and the gateway. video-service sets `fallback` on detections from the ml-engine fallback path. analytics drops `ingestedAt`. `streamSessionId` stays null for chat today: chat-service has no capture-session concept, and analytics already falls back to keying by streamer.

**History reads match what was published.** `SponsorDetectionEntity.toEvent()` derives `fallback` from the persisted model version (`SponsorDetectionEvent.isFallbackModelVersion`, the same rule the producer uses), so `GET /api/video/detections/recent` no longer returns `fallback: null` for a detection that went to Kafka with `fallback: true`; `SponsorDetectionEntityTest` covers the round trip. `docs/contracts/sentiment-pipeline.md` points at the renamed schema files.

**CI** (`schema-compat` job): `tools/schema/check_compat.py` diffs every schema against the pull request's base branch (or the previous commit on a push to `main`) and fails on a new required property, a removed property, a narrowed type, a lost enum value, an enum on a previously free property, or any validation keyword that is added or tightened (`minimum`/`maximum` and their exclusive forms, `minLength`/`maxLength`, `minItems`/`maxItems`, `minProperties`/`maxProperties`, `pattern`, `format`, `const`, `multipleOf`, `uniqueItems`), recursing into nested `properties` and `items`; it exits 2 when the base ref does not resolve, so an unfetched ref cannot make every schema look new; it also lists the base ref's schemas, so a schema that disappears fails unless the script's `RENAMED` map declares its new name, in which case the renamed file is compared against the old one (the four renames in this branch are declared there); `additionalProperties` turning false is a warning because only the contract tests enforce it. `docs/schemas/**` also counts as a shared Java change, so every Java module's contract tests run when a schema moves.

## Deliberately left alone

- No runtime validation in consumers. The tests pin the contract at build time; validating every record on the hot path is a separate decision with a cost.
- No CloudEvents envelope or schema registry. The events stay plain JSON; the `$id`s are identifiers, not fetched.
- The persisted sentiment record does not store the session fields, so `GET /api/sentiment/recent` served from Postgres returns them null. Adding the four nullable columns is a small follow-up that needs a Flyway migration.
- The gateway's GraphQL types do not expose the session fields yet (branch 11 territory).

## Verification

| Check | Command | Result |
|---|---|---|
| Full reactor build and tests | `mvn -B -ntp -Dmaven.gitcommitid.skip=true clean verify` at the root in `maven:3.9-eclipse-temurin-21` | BUILD SUCCESS, all 9 modules, 0 failures (five `EventContractTest`s and `SentimentServiceSessionFieldsTest` included) |
| video-capture-service | `uv sync --locked && ruff check … && pytest` in `python:3.11.16-slim` | ruff clean, 47 passed |
| ml-engine | same | ruff clean, 70 passed, 1 skipped |
| Compatibility check | `python tools/schema/check_compat.py --base origin/main` | exit 0 (renamed files are new; the two transcript schemas warn about `additionalProperties`) |
| Workflow syntax | `actionlint .github/workflows/ci.yml` | OK |
| Compose renders | `docker compose config -q` | OK |

## Manual checks for the reviewer

1. `git diff origin/main -- docs/schemas/` reads as: rename, add optional fields, add `$id`, tighten types. Nothing required was added, nothing removed.
2. `make up`, post a synthetic chat message, then read `stream.chat.messages` in Kafka UI: the record has `"source":"MANUAL"` and `"channelLogin"` equal to the streamer.
3. With `make twitch-up` (or the VOD replay alias), chat records carry `"source":"TWITCH"` / `"TWITCH_VOD_REPLAY"` and the matching sentiment record on `stream.sentiment.events` carries the same values.
4. Break a schema on purpose (for example add `"foo"` to `required` in `chat-message-event.schema.json`) and run `python tools/schema/check_compat.py`: it exits 1 naming the property.

## Follow-ups (not in this branch)

- Persist the session fields on sentiment records (Flyway migration) so history matches the live stream.
- Expose `source`/`streamSessionId` in the GraphQL types once the frontend can filter by session.
