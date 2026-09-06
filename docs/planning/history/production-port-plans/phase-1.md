# Phase 1: Twitch Identity And Live Chat Ingestion

## Phase Goal

Phase 1 replaces manual chat seeding with real Twitch chat ingestion while preserving the existing StreamSense microservice boundaries.

By the end of this phase, a configured Twitch channel should produce live chat messages that flow through:

```text
Twitch chat -> chat-service -> Kafka stream.chat.messages -> sentiment-service -> ml-engine -> Postgres -> Kafka stream.sentiment.events -> api-gateway -> GraphQL subscriptions/frontend
```

This phase does not include real Twitch video capture, real sponsor/logo inference, aggregate product metrics, or campaign-level recommendation upgrades. Those are later phases.

## Current Starting Point

Already available in the repo:

- `chat-service` exposes `POST /api/chat/ingest` for synthetic chat events.
- `chat-service` publishes chat events to Kafka topic `stream.chat.messages`.
- `sentiment-service` consumes `stream.chat.messages`.
- `sentiment-service` calls `ml-engine` for sentiment inference.
- sentiment rows are persisted in Postgres.
- sentiment events are published to `stream.sentiment.events`.
- `api-gateway` exposes chat and sentiment GraphQL subscriptions.
- `frontend` renders live chat and sentiment panels by streamer key.
- Docker Compose can run the full local stack.

Main missing capability:

- There is no Twitch connector. Chat only enters the system when a local script or user manually posts JSON into the gateway or service.

## Phase 1 Target Behavior

Given a Twitch channel such as `shroud`, `xqc`, or a test account channel:

1. StreamSense resolves and stores Twitch channel identity.
2. StreamSense connects to live Twitch chat.
3. Incoming Twitch chat messages are normalized into StreamSense chat events.
4. The normalized events are published to `stream.chat.messages`.
5. Existing sentiment processing continues without bypassing any service.
6. GraphQL subscriptions deliver real Twitch chat and sentiment events.
7. The frontend can display real live chat and real sentiment derived from that chat.
8. Operators can see whether Twitch ingestion is connected, reconnecting, failing, or idle.
9. Tests cover the connector behavior with mocked Twitch inputs.

## Ownership Split

### Work I Can Do In The Repo

I can implement and test the code/config/docs inside this repository:

- Add Twitch configuration properties.
- Add secret placeholders and environment variable wiring.
- Add Twitch identity models and DTOs.
- Add a Twitch chat connector inside `chat-service` or prepare a dedicated service if we choose that path.
- Normalize Twitch IRC messages into `ChatMessageEvent`.
- Publish normalized messages to Kafka.
- Add connection lifecycle endpoints and metrics.
- Add unit and integration tests with mocked Twitch sources.
- Update Docker Compose and Kubernetes manifests with required env vars.
- Update GraphQL/frontend surfaces for ingestion status if needed.
- Update runbooks and smoke scripts.

### Work You Need To Provide Or Decide

You need to provide product/account decisions and external Twitch access:

- Decide which Twitch account or bot account StreamSense should use for chat access.
- Create or identify a Twitch Developer application.
- Provide Twitch app client ID.
- Provide Twitch app client secret if Helix/EventSub is used.
- Provide a Twitch OAuth token if IRC chat access requires authenticated connection.
- Decide whether Phase 1 should support one configured channel or multiple concurrent channels.
- Provide at least one real Twitch channel for manual verification.
- Decide whether the first live proof can use your own test channel or a public active channel.
- Confirm whether storing raw chat messages is acceptable for your use case.
- Confirm retention expectations for chat and sentiment history.
- Confirm whether streamer login strings are sufficient for the UI in Phase 1, or whether Twitch display names/profile metadata are required immediately.

Do not commit real Twitch secrets to the repo. Secrets should be passed through local env vars, Docker Compose overrides, Kubernetes secrets, or a secret manager later.

## Key Product Decisions Before Implementation

### Decision 1: Connector Location

Recommended choice for Phase 1:

- Implement Twitch chat ingestion inside `chat-service`.

Reason:

- `chat-service` already owns chat ingestion and Kafka publishing.
- This avoids adding another service before the live chat path is proven.
- A dedicated `twitch-ingestion-service` can still be extracted later if ingestion becomes large.

Alternative:

- Add a new `twitch-ingestion-service` that publishes to `stream.chat.messages`.

Tradeoff:

- Cleaner separation long term, but more Docker/Kubernetes/config/test work now.

Phase 1 default unless you choose otherwise:

- Use `chat-service`.

### Decision 2: Twitch Chat Transport

Recommended choice for Phase 1:

- Use Twitch IRC for live chat messages.

Reason:

- Twitch IRC is the direct path for live chat ingestion.
- EventSub is better for lifecycle notifications, not full chat message firehose in the same simple way.
- Helix is needed for channel/user metadata, not live chat streaming.

Phase 1 default:

- Twitch IRC for chat.
- Helix optional for identity resolution if credentials are available.
- EventSub deferred to stream lifecycle work unless needed immediately.

### Decision 3: Channel Scope

Recommended Phase 1 scope:

- Start with one configured Twitch channel at runtime.
- Add support for a comma-separated list of channels if the implementation remains simple.

Reason:

- One channel proves the full path with less lifecycle complexity.
- Multi-channel subscription management can be hardened after one-channel ingestion works.

### Decision 4: Identity Fields

Recommended Phase 1 event identity additions:

- `channelLogin`
- `twitchUserId`, nullable if Helix lookup is not configured yet
- `twitchMessageId`, nullable if unavailable
- `ingestedAt`
- `source = TWITCH`

Deferred to Phase 2 or metrics phase:

- full `streamSessionId`
- active Twitch stream ID
- video timestamp alignment
- stream category/title snapshots

Reason:

- Phase 1 should avoid breaking all downstream contracts before live chat is proven.
- We can preserve the existing `streamer` field and map it to Twitch channel login.

## Implementation Plan From My End

### Step 1: Add Twitch Configuration Model

Files likely involved:

- `chat-service/src/main/java/...`
- `config-server/config-repo/chat-service.yml`
- `k8s/config/config-server-config-repo.yaml`
- `docker-compose.yml`

Add config properties:

```yaml
streamsense:
  twitch:
    chat:
      enabled: false
      channels: []
      username: ""
      oauth-token: ""
      reconnect-delay-ms: 5000
      max-reconnect-delay-ms: 60000
      connection-timeout-ms: 10000
    helix:
      enabled: false
      client-id: ""
      client-secret: ""
```

Expected implementation details:

- Keep Twitch ingestion disabled by default so existing tests and demos stay stable.
- Enable it only when `STREAMSENSE_TWITCH_CHAT_ENABLED=true` or equivalent config is provided.
- Map secrets from env vars, not committed YAML values.
- Validate required fields when Twitch chat is enabled.
- Fail fast with a clear error if enabled but credentials/channels are missing.

Acceptance criteria:

- `chat-service` starts unchanged with Twitch disabled.
- `chat-service` exposes effective config safely without logging secrets.
- Docker Compose supports enabling Twitch chat through env vars.

### Step 2: Add Twitch Chat Connector Abstraction

Files likely involved:

- `chat-service/src/main/java/com/streamsense/chatservice/twitch/`

Add interfaces/classes:

- `TwitchChatConnector`
- `TwitchChatMessage`
- `TwitchChatConnectionProperties`
- `TwitchChatLifecycleService`
- `TwitchChatMessageHandler`

Responsibilities:

- connect to Twitch IRC
- join configured channels
- parse IRC messages
- extract username, channel, message text, Twitch tags if available
- handle PING/PONG heartbeat
- reconnect on socket failure
- stop cleanly during service shutdown

Acceptance criteria:

- Connector can be unit-tested with mocked socket/input stream behavior.
- Connector does not publish directly to Kafka; it hands normalized messages to the existing chat ingestion path or a small publisher service.
- Connector logs lifecycle events without logging OAuth tokens.

### Step 3: Normalize Twitch Messages Into Existing Chat Events

Files likely involved:

- existing chat event model classes
- existing chat ingestion/publishing service
- schema docs under `docs/schemas/`

Mapping:

```text
Twitch channel login -> streamer
Twitch chatter login -> user
Twitch message text -> message
Twitch tmi-sent-ts or local receive time -> timestamp
Generated event ID or Twitch message ID -> eventId/source metadata
```

Recommended additions:

- Keep existing fields required.
- Add optional fields only if downstream JSON handling supports them safely.
- Prefer adding `source`, `ingestedAt`, and `externalMessageId` after confirming current schema tests can be updated cleanly.

Acceptance criteria:

- Existing synthetic `POST /api/chat/ingest` still works.
- Twitch messages produce the same Kafka topic as synthetic messages.
- `sentiment-service` processes Twitch-origin chat without code changes or with minimal compatible updates.

### Step 4: Add Runtime Control And Status Endpoint

Files likely involved:

- `chat-service` controller package
- `api-gateway` routes if exposed through `/api/**`
- frontend status components if included in Phase 1

Add endpoint examples:

```text
GET /api/chat/twitch/status
POST /api/chat/twitch/connect
POST /api/chat/twitch/disconnect
```

Phase 1 minimum:

- `GET /api/chat/twitch/status`

Possible status response:

```json
{
  "enabled": true,
  "connected": true,
  "channels": ["examplechannel"],
  "lastMessageAt": 1710000000000,
  "lastError": null,
  "reconnectAttempts": 0
}
```

Acceptance criteria:

- Status endpoint works through `chat-service` directly.
- Status endpoint works through `api-gateway` if route policy allows it.
- Status reflects disabled, connecting, connected, reconnecting, and failed states.

### Step 5: Add Metrics And Logs

Add Micrometer metrics:

```text
streamsense_twitch_chat_connected
streamsense_twitch_chat_messages_total
streamsense_twitch_chat_reconnects_total
streamsense_twitch_chat_errors_total
streamsense_twitch_chat_parse_failures_total
streamsense_twitch_chat_last_message_age_seconds
```

Add logs for:

- connector startup
- channel join success/failure
- reconnect attempts
- parse failures
- shutdown

Acceptance criteria:

- Metrics appear in Prometheus when Twitch ingestion is enabled.
- Metrics do not require real Twitch credentials in normal local demo mode.
- Logs are useful for debugging connection problems.

### Step 6: Add Tests

Test categories:

- config binding tests
- IRC parser unit tests
- connector lifecycle tests with fake input
- message normalization tests
- Kafka publishing integration test if feasible
- GraphQL subscription regression test using Twitch-origin event shape

Specific cases:

- parses normal `PRIVMSG`
- responds to `PING`
- ignores unsupported IRC commands
- handles empty/invalid messages
- reconnects after connection loss
- does not start when disabled
- fails clearly when enabled without required credentials
- preserves existing synthetic ingest behavior

Acceptance criteria:

- `cd chat-service && mvn -B -ntp clean test` passes.
- Any touched downstream service tests pass.
- Existing smoke path still works with Twitch disabled.

### Step 7: Update Frontend For Phase 1 Visibility

Minimum frontend change:

- Show Twitch ingestion status somewhere on the dashboard.

Possible additions:

- status pill: `Twitch connected`, `Twitch disabled`, `Twitch reconnecting`, `Twitch error`
- last received chat timestamp
- active channel list

Acceptance criteria:

- User can tell whether data is live Twitch data or demo/synthetic data.
- Existing live chat panel still works for `streamer` selection.

### Step 8: Update Runbooks And Docs

Files likely involved:

- `docs/howtorun.md`
- `production-plan.md`
- this file
- possibly `README.md`

Add documentation for:

- Twitch developer app setup
- required env vars
- local Docker Compose enablement
- how to verify status endpoint
- how to verify Kafka topic receives real Twitch messages
- how to verify GraphQL subscriptions receive real Twitch messages
- how to verify sentiment history derives from Twitch messages
- troubleshooting auth, IRC connection, and no-message states

Acceptance criteria:

- A developer with Twitch credentials can follow the runbook without reading code.
- The docs clearly distinguish synthetic demo mode from real Twitch ingestion mode.

## Required Work From Your End

### 1. Twitch Developer Application

You need to create or provide access to a Twitch Developer application.

Required values:

```text
TWITCH_CLIENT_ID
TWITCH_CLIENT_SECRET
```

Where to get them:

- Twitch Developer Console
- Create application
- Set OAuth redirect URL if using OAuth authorization flow

Phase 1 note:

- If we only use IRC with a generated OAuth token and no Helix lookup, `TWITCH_CLIENT_SECRET` may not be needed immediately.
- It is still useful to have both values ready because identity resolution will likely use Helix.

### 2. Twitch IRC OAuth Token

You need to provide an OAuth token for the Twitch user/bot that will connect to chat.

Required values:

```text
TWITCH_CHAT_USERNAME
TWITCH_CHAT_OAUTH_TOKEN
```

Important:

- The token should not be committed to git.
- The token should be passed through local environment variables or a local override file ignored by git.
- Use a bot/test account if you do not want your personal account tied to ingestion.

### 3. Test Channel

You need to choose at least one Twitch channel for verification.

Recommended options:

- Your own Twitch test channel, if you can generate chat messages reliably.
- A public active channel, if passive ingestion is enough.
- A secondary account posting messages into your own channel for controlled testing.

Needed value:

```text
TWITCH_CHANNELS=channel_login_here
```

### 4. Data And Privacy Decision

You need to confirm whether Phase 1 can persist raw chat message text.

Current platform behavior:

- Chat message text is included in chat events.
- Sentiment history stores original message text.

Decision needed:

- Keep raw messages in Postgres for demo/product history.
- Redact or hash usernames.
- Drop raw messages after sentiment processing.
- Add retention policy now or defer to production hardening.

Recommended Phase 1 default:

- Keep raw message text and usernames in local/dev only.
- Document that production retention/privacy policy is required before public launch.

### 5. Product Scope Decision

You need to decide whether Phase 1 is:

- one configured channel only, or
- multiple configured channels from the start.

Recommended Phase 1 default:

- one channel first.

Reason:

- This proves the whole pipeline faster.
- Multi-channel management can be added after the connector is stable.

### 6. Manual Verification Availability

You need to be available to help verify real Twitch behavior because credentials and live channel state are external.

Manual verification requires:

- a live/accessible channel
- chat activity
- valid token
- confirmation that the token is allowed to join/read the target channel

## Local Environment Variables

Proposed local variables:

```bash
STREAMSENSE_TWITCH_CHAT_ENABLED=true
TWITCH_CHAT_USERNAME=your_bot_or_user_name
TWITCH_CHAT_OAUTH_TOKEN=oauth:your_token_here
TWITCH_CHANNELS=target_channel_login
TWITCH_CLIENT_ID=your_client_id
TWITCH_CLIENT_SECRET=your_client_secret
```

Security notes:

- Do not put real values in committed files.
- Use shell exports, `.env.local` ignored by git, Docker Compose override, or Kubernetes Secret.
- Redact tokens from logs and screenshots.

## Expected Code-Level Changes

Likely touched areas:

```text
chat-service/
config-server/config-repo/chat-service.yml
k8s/config/config-server-config-repo.yaml
docker-compose.yml
docs/howtorun.md
docs/schemas/chat-message-event.json
api-gateway/ possibly only route/status exposure
frontend/ possibly status display
tools/smoke/ possibly Twitch-disabled regression coverage
```

Expected new package shape inside `chat-service`:

```text
com.streamsense.chatservice.twitch
  TwitchChatProperties
  TwitchChatConnector
  TwitchChatLifecycleService
  TwitchIrcMessageParser
  TwitchChatStatus
  TwitchChatMessage
```

Expected reuse of existing code:

- Existing chat event publisher should remain the publishing boundary.
- Existing `POST /api/chat/ingest` should remain available for tests, local demos, and fallback.
- Existing Kafka topic should remain `stream.chat.messages`.
- Existing downstream sentiment service should not need to know whether a message came from Twitch or synthetic ingest unless optional source fields are added.

## Testing Plan

### Automated Tests I Should Add

Unit tests:

- Twitch IRC parser parses `PRIVMSG` with tags.
- Twitch IRC parser extracts message ID if present.
- Twitch IRC parser extracts sent timestamp if present.
- Twitch IRC parser handles missing tags.
- Twitch IRC parser ignores unsupported commands.
- Twitch connector responds to `PING` with `PONG`.
- Twitch connector does not start when disabled.
- Twitch config validation fails when enabled without username/token/channel.
- Twitch message normalizer creates valid chat events.

Integration-style tests:

- fake Twitch input produces Kafka chat event.
- existing synthetic ingest still produces Kafka chat event.
- sentiment-service can consume Twitch-origin event shape.

Frontend tests if UI status is added:

- shows disabled state.
- shows connected state.
- shows error/reconnecting state.

### Manual Tests You And I Need To Run

Test 1: Twitch disabled regression

```bash
make smoke-e2e
```

Expected:

- existing synthetic E2E path still passes.

Test 2: Start stack with Twitch enabled

```bash
STREAMSENSE_TWITCH_CHAT_ENABLED=true \
TWITCH_CHAT_USERNAME=... \
TWITCH_CHAT_OAUTH_TOKEN=... \
TWITCH_CHANNELS=... \
make up
```

Expected:

- stack starts successfully.
- `chat-service` logs Twitch connector startup.
- status endpoint says connected or gives a clear failure reason.

Test 3: Confirm chat reaches Kafka

```bash
docker compose exec kafka kafka-console-consumer \
  --bootstrap-server kafka:9092 \
  --topic stream.chat.messages \
  --from-beginning \
  --timeout-ms 10000
```

Expected:

- real Twitch chat messages appear as StreamSense chat events.

Test 4: Confirm GraphQL chat subscription

```bash
npx wscat -c ws://localhost:8080/graphql -s graphql-transport-ws
```

Then:

```json
{"type":"connection_init"}
```

Then:

```json
{
  "id":"1",
  "type":"subscribe",
  "payload":{
    "query":"subscription($streamer:String!){ onChatMessage(streamer:$streamer){ eventId streamer user message timestamp } }",
    "variables":{"streamer":"target_channel_login"}
  }
}
```

Expected:

- real Twitch messages appear in the subscription.

Test 5: Confirm sentiment pipeline

```bash
curl -X POST http://localhost:8080/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"query($streamer:String!,$limit:Int!){ recentSentiment(streamer:$streamer, limit:$limit){ streamer user message label score modelVersion }}","variables":{"streamer":"target_channel_login","limit":10}}'
```

Expected:

- sentiment rows exist for real Twitch messages.

Test 6: Confirm frontend

```text
http://localhost:3000
```

Expected:

- live chat panel receives Twitch messages.
- sentiment panel updates for the same streamer.
- UI clearly shows whether Twitch ingestion is active if status work is included.

Test 7: Confirm metrics

Prometheus queries:

```promql
streamsense_twitch_chat_messages_total
streamsense_twitch_chat_connected
streamsense_chat_ingest_total
streamsense_sentiment_events_total
```

Expected:

- Twitch message metric increases.
- Existing chat/sentiment metrics increase.

## Failure Modes To Handle

Expected failures:

- invalid OAuth token
- missing channel
- channel has no chat activity
- Twitch IRC disconnects
- network timeout
- malformed IRC line
- duplicate message delivery
- Kafka temporarily unavailable
- downstream sentiment lag

Required behavior:

- No service crash for ordinary Twitch disconnects.
- Clear status endpoint error.
- Reconnect with bounded backoff.
- Metrics reflect errors and reconnects.
- No token leakage in logs.
- Existing synthetic ingest remains available even if Twitch connector fails.

## Phase 1 Acceptance Criteria

Phase 1 is complete when all of these are true:

- Twitch ingestion can be disabled and the existing demo/smoke path still works.
- Twitch ingestion can be enabled with environment variables.
- `chat-service` connects to at least one Twitch channel.
- Real Twitch chat messages are published to `stream.chat.messages`.
- Existing GraphQL `onChatMessage(streamer)` emits real Twitch messages.
- Existing sentiment pipeline processes real Twitch messages.
- `recentSentiment(streamer, limit)` returns rows derived from real Twitch messages.
- Frontend displays live Twitch chat and sentiment for the configured streamer.
- Status endpoint or UI makes ingestion state visible.
- Prometheus exposes Twitch ingestion metrics.
- Automated tests cover parser, config validation, normalization, and disabled-mode regression.
- Documentation explains how to configure and verify Twitch ingestion without committing secrets.

## Out Of Scope For Phase 1

- Twitch video capture.
- Sponsor/logo detection from real video frames.
- StreamSession persistence as a fully complete product model.
- EventSub lifecycle automation.
- Multi-tenant user accounts.
- Campaign setup and authorization.
- Product metric aggregation.
- Recommendation algorithm changes.
- Cloud deployment hardening.
- Production retention/privacy enforcement beyond documenting the decision.

## Open Questions

1. Should Phase 1 use `chat-service` for Twitch ingestion, or do you want a dedicated `twitch-ingestion-service` from the start?
2. Will you provide a bot/test Twitch account, or should the connector use your personal Twitch account token for local testing?
3. Should the first implementation support one channel or multiple channels?
4. Is it acceptable to persist raw chat usernames and message text during Phase 1?
5. Do you want Helix identity lookup in Phase 1, or should we start with IRC-only and channel login strings?
6. What Twitch channel should be used for the first real end-to-end verification?

## Recommended Defaults

Unless you choose otherwise, use these defaults:

- Put Twitch ingestion inside `chat-service`.
- Use Twitch IRC for chat ingestion.
- Keep Helix lookup optional in Phase 1.
- Support one configured channel first.
- Preserve the existing `streamer` field as the Twitch channel login.
- Add only optional metadata fields to avoid breaking downstream services.
- Keep synthetic `POST /api/chat/ingest` for tests and local demos.
- Do not store any secrets in committed repo files.
