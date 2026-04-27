# Load Tooling

## Chat Ingest Load

Use `chat_ingest_load.py` to drive paced chat traffic through `api-gateway` and summarize both request latency and matched sentiment end-to-end latency.

Example:

```bash
python tools/load/chat_ingest_load.py \
  --base-url http://localhost:8080 \
  --rate 2 \
  --duration 30 \
  --streamers 3 \
  --output /tmp/streamsense-chat-load.json
```

What it does:

- sends paced `POST /api/chat/ingest` requests through the gateway
- captures HTTP request latency and status codes
- stores returned `eventId` values
- waits for the async pipeline to settle
- queries `GET /api/sentiment/recent` through the gateway path
- matches `sourceEventId` values back to ingested chat events
- reports end-to-end sentiment latency as `processedAt - ingest timestamp`

Notes:

- keep runs small enough that recent history queries stay within the current service `limit` cap of `100`
- the tool is intentionally dependency-light and uses only the Python standard library
- for a degraded-path run, restart `ml-engine` with `ML_ENGINE_FORCE_FAILURE=true` and then run the same command again
- default benchmark runs include gateway rate limiting, so `429` responses are valid current-system behavior
- for a deeper backend benchmark without edge rejection, restart the gateway with `STREAMSENSE_GATEWAY_RATE_LIMIT_ENABLED=false`

Rate-limit-relaxed example:

```bash
STREAMSENSE_GATEWAY_RATE_LIMIT_ENABLED=false docker compose up -d api-gateway
python tools/load/chat_ingest_load.py \
  --base-url http://localhost:8080 \
  --rate 2 \
  --duration 30 \
  --streamers 3 \
  --output /tmp/streamsense-chat-load-relaxed.json
STREAMSENSE_GATEWAY_RATE_LIMIT_ENABLED=true docker compose up -d api-gateway
```
