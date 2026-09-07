# Sprint 11 Live Compose Benchmark

## Goal

Run the actual live Sprint 11 Compose benchmark, capture the metrics honestly, and update the performance report with the measured results.

## Work Completed

### 1. Resolved local environment issues

- used Ubuntu WSL with Docker Desktop integration for the live run
- copied the repo to `/home/ujjawal/StreamSense-Production` because Maven could not reliably write build outputs on the Windows-mounted checkout
- installed/used the required runtime tools in Ubuntu WSL: Java 21, Maven, kubectl, make, Node 20, Python, and Docker CLI access

### 2. Packaged and started the stack

Executed:

```bash
make package
docker compose up -d --build
kubectl kustomize k8s
```

The stack came up cleanly and health checks passed for:

- `http://localhost:8080/actuator/health`
- `http://localhost:8083/actuator/health`
- `http://localhost:8084/actuator/health`
- `http://localhost:8000/ml/health`
- `http://localhost:9090/-/healthy`
- `http://localhost:3001/api/health`

### 3. Verified frontend and repo checks

Executed:

```bash
cd frontend && npm run test
make test
```

Result:

- frontend Vitest suite passed
- repo-level tests passed from the Ubuntu copy

### 4. Ran the baseline load benchmark

Executed:

```bash
python3 tools/load/chat_ingest_load.py --base-url http://localhost:8080 --rate 2 --duration 30 --streamers 3 --output /tmp/streamsense-baseline.json
```

Result:

- requests attempted: `60`
- requests succeeded: `48`
- HTTP p50: `13.31 ms`
- HTTP p95: `16.96 ms`
- matched sentiment events: `48 / 48`
- sentiment p50: `20.0 ms`
- sentiment p95: `176.5 ms`
- status codes: `48 x 200`, `12 x 429`

### 5. Ran the degraded-path benchmark

Executed:

```bash
ML_ENGINE_FORCE_FAILURE=true docker compose up -d ml-engine
python3 tools/load/chat_ingest_load.py --base-url http://localhost:8080 --rate 2 --duration 30 --streamers 3 --output /tmp/streamsense-degraded.json
ML_ENGINE_FORCE_FAILURE=false docker compose up -d ml-engine
```

Result:

- requests attempted: `60`
- requests succeeded: `14`
- HTTP p50: `3.77 ms`
- HTTP p95: `13.18 ms`
- matched sentiment events: `4`
- unmatched events: `10`
- sentiment p50: `9108.0 ms`
- sentiment p95: `10049.5 ms`
- status codes: `14 x 200`, `46 x 429`

### 6. Updated the performance report

Updated:

- `docs/performance-report.md`

The report now includes:

- the live baseline metrics
- the degraded-path metrics
- the Ubuntu WSL / Docker Desktop environment details
- the gateway rate-limiting limitation observed during the run

## Observed Limitation

- gateway rate limiting produced `429` responses during the benchmark, so the run reflects the current edge policy rather than a pure saturation test

## Files Changed

- `docs/performance-report.md`
- `opencodeCommandHistory/2026-04-23-sprint-11-live-compose-benchmark-and-metrics.md`

## Recommended Next Step

- tune or profile-gate gateway rate limiting if the benchmark should measure deeper downstream behavior instead of edge rejection
