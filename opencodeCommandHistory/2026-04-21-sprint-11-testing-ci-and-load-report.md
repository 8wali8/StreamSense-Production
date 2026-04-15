# Sprint 11 Command History

## Goal

Begin Sprint 11 by shipping the first concrete pieces of the testing, CI hardening, and load-reporting plan:

- add contract protection for documented event schemas
- add repeatable load tooling under `tools/`
- add performance-oriented dashboarding
- add service metrics needed for honest latency reporting
- verify the new work and document the current blocker for live Compose benchmarking

## Starting Point Assessment

After reviewing the repo before editing:

- CI was already stronger than the old roadmap baseline: Java tests, ML lint/tests, frontend lint/tests/build, Kubernetes render validation, and Docker smoke already existed
- GraphQL contract protection already existed in `api-gateway` through `GraphqlSchemaContractTest`
- no automated protection existed for the JSON event schemas under `docs/schemas/`
- no `tools/` directory existed for repeatable load generation
- no performance dashboard existed beyond the sprint-specific observability dashboards
- no service-level persistence latency or end-to-end latency timers existed for sentiment or sponsor flows
- `docs/schemas/sentiment-analysis-event.json` had drifted from the implemented contract: it still used `timestamp` while the code and downstream consumers used `chatTimestamp`

## Work Completed

### 1. Fixed documented contract drift

- updated `docs/schemas/sentiment-analysis-event.json`
  - changed `timestamp` to `chatTimestamp`
  - added `additionalProperties: false`

This aligns the schema with:

- `sentiment-service` event model
- `api-gateway` event model
- frontend GraphQL usage
- `docs/contracts/sentiment-pipeline.md`

### 2. Added JSON schema contract tests

Added:

- `chat-service/src/test/java/com/streamsense/chatservice/events/ChatMessageSchemaContractTest.java`
- `sentiment-service/src/test/java/com/streamsense/sentimentservice/events/SentimentAnalysisSchemaContractTest.java`
- `video-service/src/test/java/com/streamsense/videoservice/events/VideoEventSchemaContractTest.java`

These tests verify that the documented JSON schemas match the actual serialized event shapes used by the services.

### 3. Added performance metrics needed for Sprint 11

Updated:

- `sentiment-service/src/main/java/com/streamsense/sentimentservice/metrics/SentimentMetrics.java`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/service/SentimentService.java`
- `video-service/src/main/java/com/streamsense/videoservice/metrics/VideoMetrics.java`
- `video-service/src/main/java/com/streamsense/videoservice/service/VideoProcessingService.java`

Added metrics:

- `streamsense_sentiment_persistence_latency_ms`
- `streamsense_sentiment_end_to_end_latency_ms`
- `streamsense_sponsor_persistence_latency_ms`
- `streamsense_sponsor_end_to_end_latency_ms`

### 4. Added repeatable load tooling

Added:

- `tools/load/chat_ingest_load.py`
- `tools/load/README.md`

The first load tool targets the chat ingest path and computes:

- HTTP request latency summary
- request success and failure counts
- matched end-to-end sentiment latency by correlating returned `eventId` values with `sourceEventId` values from recent sentiment history

### 5. Added performance dashboard provisioning

Added:

- `monitoring/grafana/provisioning/dashboards/performance-overview.json`

Updated:

- `k8s/config/grafana-config.yaml`

New dashboard panels:

- chat ingest rate
- total consumer lag
- cache hit ratio
- fallback rate
- end-to-end latency p95
- persistence latency p95
- ML inference latency p95
- Kafka produce and consume rates

### 6. Added the working performance report

Added:

- `docs/performance-report.md`

This first version records:

- what Sprint 11 assets now exist
- what was actually verified in this session
- the honest blocker that prevented live Compose benchmarking
- the exact first live benchmark commands to run once Docker is available

## Verification Performed

### Contract test execution

Executed:

```bash
cd chat-service && mvn -Dtest=ChatMessageSchemaContractTest test
cd sentiment-service && mvn clean -Dtest=SentimentAnalysisSchemaContractTest test
cd video-service && mvn -Dtest=VideoEventSchemaContractTest test
```

Result:

- all new schema contract tests passed

### Affected service test suites

Executed:

```bash
cd chat-service && mvn test
cd sentiment-service && mvn test
cd video-service && mvn test
```

Result:

- all three affected service suites passed after the new metrics and schema-test additions

### Load tool CLI validation

Executed:

```bash
python3 tools/load/chat_ingest_load.py --help
```

Result:

- CLI rendered correctly

### Load tool end-to-end validation against mock server

Because the real stack could not be started, the new load tool was validated against a local mock HTTP server that simulated:

- `POST /api/chat/ingest`
- `GET /api/sentiment/recent`

Result summary:

- requested count: `10`
- successful count: `10`
- HTTP request mean latency: `4.15 ms`
- HTTP request p95 latency: `14.71 ms`
- matched sentiment events: `10 / 10`
- matched sentiment end-to-end p95: `250.0 ms`

These numbers validate the load tool behavior only. They do not represent full StreamSense runtime performance.

### Manifest and dashboard validation

Executed:

```bash
kubectl kustomize k8s > /tmp/streamsense-k8s-rendered.yaml
python3 -c "import yaml, json; docs=list(yaml.safe_load_all(open('k8s/config/grafana-config.yaml'))); cm=next(d for d in docs if d['metadata']['name']=='grafana-dashboards'); [json.loads(content) for content in cm['data'].values()]; print('grafana dashboards valid')"
python3 -c "import json; json.load(open('monitoring/grafana/provisioning/dashboards/performance-overview.json')); print('performance dashboard valid')"
```

Result:

- Kubernetes manifests rendered successfully
- embedded Grafana dashboard JSON was valid
- Docker Grafana dashboard JSON was valid

## Blocker Encountered

The remaining live benchmark step could not run in this session because Docker was unavailable:

```bash
docker compose down --remove-orphans
```

Result:

- failed with `Cannot connect to the Docker daemon at unix:///Users/ujjawalprasad/.docker/run/docker.sock`

Follow-up checks confirmed the stack was not already running:

- `curl http://localhost:8080/actuator/health` failed
- `curl http://localhost:8083/actuator/health` failed
- `curl http://localhost:9090/-/healthy` failed
- `curl http://localhost:3001/api/health` failed

## Files Changed

- `docs/schemas/sentiment-analysis-event.json`
- `chat-service/src/test/java/com/streamsense/chatservice/events/ChatMessageSchemaContractTest.java`
- `sentiment-service/src/test/java/com/streamsense/sentimentservice/events/SentimentAnalysisSchemaContractTest.java`
- `video-service/src/test/java/com/streamsense/videoservice/events/VideoEventSchemaContractTest.java`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/metrics/SentimentMetrics.java`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/service/SentimentService.java`
- `video-service/src/main/java/com/streamsense/videoservice/metrics/VideoMetrics.java`
- `video-service/src/main/java/com/streamsense/videoservice/service/VideoProcessingService.java`
- `tools/load/chat_ingest_load.py`
- `tools/load/README.md`
- `monitoring/grafana/provisioning/dashboards/performance-overview.json`
- `k8s/config/grafana-config.yaml`
- `docs/performance-report.md`

## Recommended Next Step

Once Docker is available, finish this Sprint 11 slice by running the first real Compose benchmark and updating `docs/performance-report.md` with:

- baseline request latency
- matched sentiment end-to-end latency
- degraded-path behavior with `ML_ENGINE_FORCE_FAILURE=true`
- dashboard observations from Grafana and Prometheus under load
