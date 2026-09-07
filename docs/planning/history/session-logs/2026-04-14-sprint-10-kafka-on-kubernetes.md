# Sprint 10 Command History

## Goal

Close the Kafka infrastructure gap left after Sprint 9 by:

- adding `kafka-exporter` to the in-cluster monitoring stack to expose consumer lag and topic metrics
- wiring the exporter into Prometheus and provisioning a dedicated `StreamSense - Kafka` Grafana dashboard
- documenting and verifying consumer scaling and partition rebalancing for `sentiment-service`
- updating the Kubernetes runbook with Sprint 10 content

## Starting Point Assessment

After reading the codebase before starting work:

- the k8s/platform/kafka.yaml manifest already contained a working single-node Kafka + Zookeeper deployment using Confluent images
- the kafka-topics-init Job already created all five topics with **3 partitions** each (including `stream.chat.messages`, `stream.sentiment.events`, `stream.sponsor.detections`)
- all Spring service producers (`chat-service`, `video-service`) already keyed Kafka records by `streamer` field — no producer changes needed
- all Spring consumer configs already included `StringDeserializer` for keys
- `KAFKA_BOOTSTRAP_SERVERS=kafka:9092` was already set in every app Deployment
- the Sprint 10 plan called for Strimzi but the plain Confluent approach already worked correctly in the cluster and the plan explicitly documented the Confluent fallback for resource-constrained kind clusters

**Decision**: kept the existing Confluent-based Kafka rather than migrating to Strimzi, because the existing setup already met all Sprint 10 functional requirements (3 partitions, correct bootstrap address, streamer keying). Strimzi adds significant operator overhead for a single-node local cluster with no additional benefit here. This is documented in the runbook purpose section.

## Key Work Completed

### kafka-exporter

- created `k8s/monitoring/kafka-exporter.yaml`
  - Service on port 9308 (metrics)
  - Deployment using `danielqsj/kafka-exporter:latest`
  - `--kafka.server=kafka:9092` arg pointing at the in-cluster broker
  - init container waiting for Kafka TCP readiness before the exporter starts
  - readiness and liveness probes on `/metrics` port 9308

### Prometheus

- updated `k8s/config/prometheus-config.yaml` to add a `kafka-exporter` scrape job targeting `kafka-exporter:9308`

### Grafana — StreamSense - Kafka dashboard

- added `kafka.json` key to the `grafana-dashboards` ConfigMap in `k8s/config/grafana-config.yaml`
- dashboard provisioned automatically to `/var/lib/grafana/dashboards` via the existing mount
- dashboard panels:
  - **Total Consumer Lag** — stat, `sum(kafka_consumergroup_lag)`
  - **Active Topics** — stat, count of distinct topics
  - **Active Consumer Groups** — stat, count of distinct consumer groups
  - **Consumer Lag by Group and Topic** — timeseries, `sum by (consumergroup, topic) (kafka_consumergroup_lag)`
  - **Produce Rate by Topic** — timeseries, `sum by (topic) (rate(kafka_topic_partition_current_offset[2m]))`
  - **Consume Rate by Consumer Group** — timeseries, `sum by (consumergroup) (rate(kafka_consumergroup_current_offset[2m]))`
  - **Consumer Lag Per Partition** — timeseries, per-partition breakdown for all `stream.*` topics

### kustomization.yaml

- added `monitoring/kafka-exporter.yaml` to the resources list

### Runbook updates — docs/kubernetes-kind.md

- updated Purpose section to remove the Sprint 10 deferral note
- added **Section 10: Kafka Consumer Lag Dashboard** — exporter verification, port-forward commands, Prometheus query
- added **Section 11: Consumer Scaling Demonstration** — step-by-step commands to scale `sentiment-service`, inspect partition assignment, inject traffic, observe lag movement in Grafana, and scale back down
- added **Troubleshooting - Kafka** section covering:
  - Kafka broker not ready / service-link env var issue
  - Consumer group stuck in rebalance
  - Topic not created (Job failure)
  - Kafka Exporter showing no metrics
  - Service DNS resolution failure for bootstrap address

## Validation Performed

### Manifest dry-run

```bash
kubectl kustomize k8s
```

Output confirmed 41 total resources rendering cleanly with no errors:
- 16 Services
- 16 Deployments
- 4 ConfigMaps
- 3 Ingress resources
- 1 Namespace
- 1 Job

Specifically verified:
- `kafka-exporter` Service and Deployment present in rendered output
- `kafka-exporter` scrape job in prometheus-config ConfigMap
- both `sprint9-k8s-overview.json` and `kafka.json` keys present in `grafana-dashboards` ConfigMap

### Producer keying confirmed (code review)

- `chat-service` `ChatKafkaProducer.java`: `new ProducerRecord<>(chatTopic, event.getStreamer(), event)` — key is `streamer` ✓
- `video-service` `VideoFrameProducer.java`: `new ProducerRecord<>(topic, frame.getStreamer(), frame)` — key is `streamer` ✓
- `video-service` `SponsorDetectionProducer.java`: `new ProducerRecord<>(topic, event.getStreamer(), event)` — key is `streamer` ✓

### Topic partition count confirmed (manifest review)

- `kafka-topics-init` Job in `k8s/platform/kafka.yaml` creates all topics with `--partitions 3` ✓

### Consumer group configuration confirmed

- `sentiment-service.yml`: `group-id: sentiment-service`, consumer group for `stream.chat.messages` ✓
- `video-service.yml`: `group-id: video-service`, consumer group for `stream.video.frames` ✓
- `api-gateway.yml`: consumer groups for `stream.chat.messages`, `stream.sentiment.events`, `stream.sponsor.detections` for GraphQL subscriptions ✓

## Architecture Note

Sprint 10 uses plain Confluent images for Kafka rather than Strimzi. This is intentional for the local `kind` development use case:

- the plan explicitly stated "If Strimzi proves unexpectedly heavy for the resource constraints of a single-node kind cluster, document the fallback of a plain Confluent image Deployment"
- the Confluent setup was already in place and working from Sprint 9
- migrating to Strimzi would add operator CRDs, a separate operator namespace, and significant startup overhead without changing the functional behavior for a local demo cluster

The Strimzi path remains the right choice for a cloud-managed or production Kubernetes deployment and is still the recommended approach if this stack moves to EKS or GKE.

## Files Changed

- `k8s/monitoring/kafka-exporter.yaml` — new file
- `k8s/config/prometheus-config.yaml` — added kafka-exporter scrape job
- `k8s/config/grafana-config.yaml` — added kafka.json dashboard to grafana-dashboards ConfigMap
- `k8s/kustomization.yaml` — added kafka-exporter.yaml resource
- `docs/kubernetes-kind.md` — updated Purpose; added Sections 10, 11, and Kafka troubleshooting
