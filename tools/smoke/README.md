# Smoke Tools

`compose_smoke.py` is the final API-level smoke path for the local Compose demo.

Default behavior checks an already-running stack:

```bash
python tools/smoke/compose_smoke.py
```

Full clean-state smoke run:

```bash
python tools/smoke/compose_smoke.py --start-compose --teardown
```

What it verifies:

- core service health endpoints
- chat ingest through `api-gateway`
- video frame ingest through `api-gateway`
- GraphQL `health`
- GraphQL `recentSentiment`
- GraphQL `sponsorDetections`
- GraphQL `recommendations`
- frontend HTML
- Zipkin services endpoint

Use `--relaxed-rate-limit` when the smoke run is part of a benchmark that should avoid gateway `429` responses.

## VOD replay smoke

`replay_smoke.py` verifies the Twitch VOD replay path from `docs/replay-runbook.md` (plan history in `docs/planning/history/plans/vod-replay-testing-plan.md`): it switches chat and video capture to a replay alias (default `redbull-testing`), then polls until replay events flow through the stack.

Against an already-running stack (the stack must have been started with `STREAMSENSE_TWITCH_CHAT_ENABLED=true`; no Twitch credentials are needed for replay):

```bash
make replay-smoke
# or
python tools/smoke/replay_smoke.py
```

Full clean-state run (starts Compose with the required env, tears down after):

```bash
python tools/smoke/replay_smoke.py --start-compose --teardown
```

What it verifies:

- chat and video capture channel switches through `api-gateway`
- GraphQL `recentSentiment` receives fixture-replayed chat sentiment (offline, deterministic)
- video capture publishes frames for the alias (needs network access to Twitch)
- any `sponsorDetections` for the alias carry `source = TWITCH_VOD_REPLAY`
- with `--expect-transcripts`: `recentTranscriptSegments` is non-empty and all segments carry `source = TWITCH_VOD_REPLAY` (stack must run with `STREAMSENSE_TWITCH_TRANSCRIPT_ENABLED=true`; the first run downloads the Whisper model, so consider a higher `--deadline-seconds`)

Use `--skip-video` for a fully offline run that only checks the fixture-backed chat replay path.
