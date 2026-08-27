# Demo Tools

These helpers assume the Compose stack is running through the gateway at `http://localhost:8080`.

## Seed Demo Data

```bash
python tools/demo/seed_demo.py
```

Useful options:

- `--streamer demo-streamer`
- `--base-url http://localhost:8080`

The seed tool sends sample chat and frame events through the gateway, waits briefly, then verifies GraphQL health, sentiment history, sponsor history, and recommendations.

## Open Demo Surfaces

```bash
python tools/demo/open_demo.py
```

Use `--print-only` in terminal-only environments.

## Final Smoke Path

```bash
python tools/smoke/compose_smoke.py --start-compose --teardown
```

For backend benchmark runs that should avoid edge rejection from gateway rate limiting:

```bash
python tools/smoke/compose_smoke.py --start-compose --teardown --relaxed-rate-limit
```
