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
