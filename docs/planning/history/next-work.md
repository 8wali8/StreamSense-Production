# Next Work

This is the recommended backlog for the current StreamSense replay/demo milestone.

## Priority 1: Replay Smoke Test

**Status: done.** Landed as `tools/smoke/replay_smoke.py` behind `make replay-smoke` (Python rather than the PowerShell script sketched below).

Create:

```text
tools/smoke-replay.ps1
```

Goal: one command answers whether `redbull-testing` replay is working end-to-end.

Suggested interface:

```powershell
powershell -ExecutionPolicy Bypass -File "tools/smoke-replay.ps1" -Streamer redbull-testing -Sponsor "Red Bull" -TimeoutSeconds 120
```

Checks:

- Frontend returns `HTTP 200`.
- API Gateway health is `UP`.
- ML Engine health is ok.
- Chat status is `CONNECTED` for `redbull-testing`.
- Video status reaches `CAPTURING` for `redbull-testing`.
- At least one frame has been captured/published.
- At least one transcript segment exists for `redbull-testing`.
- At least one transcript sentiment event exists for `redbull-testing`.
- Sponsor transcript sentiment endpoint/query is reachable, even if the result is empty.
- Optional: verify `source = "TWITCH_VOD_REPLAY"` and `twitchStreamId = "2750461300"` on transcript/frame-related data.

Operator output should be sectioned by subsystem and end with either:

```text
Replay smoke check passed
```

or:

```text
Replay smoke check failed: <specific reason>
```

## Priority 2: Unified Replay Status Endpoint

Add an API Gateway endpoint such as:

```text
GET /api/replay/status?streamer=redbull-testing
```

Purpose: aggregate replay health so scripts and the frontend do not need to call every service separately.

Suggested response shape:

```json
{
  "streamer": "redbull-testing",
  "source": "TWITCH_VOD_REPLAY",
  "vodId": "2750461300",
  "healthy": true,
  "chat": {
    "state": "CONNECTED",
    "lastMessageAt": 1779054152022
  },
  "video": {
    "state": "CAPTURING",
    "lastFrameAt": 1779054112232,
    "framesPublished": 2
  },
  "transcript": {
    "lastSegmentAt": 1779054135726,
    "segmentsPublished": 2
  },
  "sentiment": {
    "lastProcessedAt": 1779054140000
  }
}
```

Implementation notes:

- Put the aggregation in `api-gateway`.
- Reuse existing chat, video, and sentiment client paths where possible.
- Keep this endpoint read-only.
- Return partial subsystem errors instead of failing the whole response when one downstream service is degraded.

## Priority 3: Frontend Replay Health Panel

Add a small visible replay diagnostics panel to the live console.

Show:

- Streamer alias
- VOD id
- Source marker
- Chat state and last message time
- Video state and last frame time
- Transcript last segment time
- Current/recent `videoTimestampMs`
- Sponsor profile status if available

This should make it obvious whether blank UI states are caused by capture, transcript, sentiment, sponsor relevance, or frontend rendering.

## Priority 4: Sponsor Relevance Persistence Or Seeding

**Status: seeding done, persistence open.** Seeds live under `streamsense.sentiment.relevance.seeds` in `config-server/config-repo/sentiment-service.yml` (`redbull-testing` → Red Bull) and are applied at startup; the runtime update endpoint still overrides them.

Current sponsor relevance profiles are runtime/in-memory. A service restart can lose the active sponsor context.

First useful version:

- Seed default sponsor profiles from config for demo/replay aliases.
- Keep the runtime update endpoint for frontend overrides.
- Add `redbull-testing` / `Red Bull` as the canonical seeded profile.

Later version:

- Persist sponsor relevance profiles in a database.
- Add admin/profile management APIs.

## Priority 5: Git Hygiene For Build Artifacts

**Status: done.** `config-server/target/**` and `eureka-server/target/**` are no longer tracked, `target/` is ignored at every depth, and `.opencode/` is local-only.

Generated Java build outputs can show as dirty after local/Docker packaging.

Recommended cleanup:

- Ensure all `target/**` paths are ignored.
- Stop tracking generated artifacts that are currently tracked, if safe.
- Do not mix this cleanup with feature changes.

Files that should remain uncommitted during normal work:

- `config-server/target/**`
- `eureka-server/target/**`
- local `session-ses_*.md` files
- `.env.twitch.local`
- captured frame/audio artifacts

## Priority 6: Replay Timeline

Once replay health is solid, build a timeline-oriented view that aligns:

- Chat messages
- Transcript segments
- Sponsor detections
- Sentiment events
- Video timestamps

This unlocks higher-level sponsor analytics, such as what chat and streamer speech looked like around a sponsor exposure.
