# Sprint 10 Implementation Plan

## Goal

Deploy Kafka to the local `kind` cluster, connect the streaming services to it, demonstrate partition-based consumer scaling, and make consumer lag and throughput visible through Prometheus and Grafana.

Sprint 9 proved the full application stack runs coherently on Kubernetes. Sprint 10 closes the last infrastructure gap: the Sprint 9 Kubernetes deployment relied on either an external or hybrid Kafka arrangement. This sprint makes Kafka a real in-cluster citizen and turns the platform's async event backbone into a demonstrable, scalable, observable subsystem.

The target runtime shape for this sprint is:

`in-cluster Kafka (via Strimzi) -> multi-partition topics -> Spring consumer services pulling from in-cluster brokers -> replica scaling -> lag and throughput visible in Grafana`

Sprint 10 is not about application logic changes. It is about infrastructure: getting Kafka into the cluster, updating service config, and producing a credible scaling and lag demonstration.

## Sprint 10 Success Criteria

Sprint 10 is complete only when all of the following are true:

- Kafka runs inside the `streamsense` namespace via Strimzi or a documented equivalent
- the following topics exist in the in-cluster Kafka with multiple partitions:
  - `stream.chat.messages`
  - `stream.sentiment.events`
  - `stream.sponsor.detections`
- all Spring services that previously pointed at Docker Compose Kafka now resolve the in-cluster Kafka broker service name
- Kafka topic-init job or equivalent creates topics reliably at cluster startup
- `sentiment-service` can be scaled to multiple replicas and partition assignment distributes correctly across replicas
- consumer lag is visible in Prometheus and Grafana
- a scaling demonstration can be reproduced with documented commands
- messages are keyed by `streamer` to preserve per-streamer ordering across partitions
- the Docker Compose workflow remains unchanged and still works for the fast local dev path
- the Kubernetes runbook is updated with Kafka-specific steps and failure modes

## Current Starting Point

This plan assumes the repo state after Sprint 9 is:

- the full application stack runs on `kind` with Deployments, Services, and Ingress for all services
- `config-server` native mode works in Kubernetes via ConfigMap mounts
- `eureka-server` provides in-cluster service discovery
- `postgres` and `redis` run in-cluster with stable DNS names
- Kafka was either excluded from the Sprint 9 cluster or run with a temporary hybrid arrangement
- the existing Sprint 9 manifests in `k8s/` are the authoritative Kubernetes deployment, not Docker Compose
- Docker Compose still includes the full Kafka/Zookeeper stack and is the primary fast dev path

The largest Sprint 10 gap is that the Kubernetes deployment has no real Kafka. Services that depend on Kafka either cannot run end-to-end in-cluster or require an external broker that is not part of the documented local workflow.

## Important Architecture Notes

Sprint 10 must preserve the architecture already established without drifting:

- keep `stream.chat.messages`, `stream.sentiment.events`, and `stream.sponsor.detections` as the canonical topic names — no renaming
- keep message keying by `streamer` so per-streamer ordering is preserved when partition counts increase
- keep Kafka as the event backbone; do not add Zookeeper-free KRaft unless it is clearly lower-friction for local `kind` usage
- keep Eureka and Config Server in-cluster — do not remove them during Kafka work
- keep Docker Compose as the working fast dev path; do not break it while adding Kubernetes Kafka

## Scope Decisions For Sprint 10

### Kafka Operator Choice

Use **Strimzi** as the default Kafka-on-Kubernetes approach.

Why:
- Strimzi is the lowest-ops-pain production-shaped option for local `kind` clusters
- it handles broker config, topic creation via `KafkaTopic` CRDs, and readiness without manual YAML juggling
- it is the choice explicitly called out in the plan for this sprint

If Strimzi proves unexpectedly heavy for the resource constraints of a single-node `kind` cluster, document the fallback of a plain Confluent image Deployment (similar to what Sprint 9 used in Docker Compose) as an alternative, and note the operator complexity tradeoff.

### Partition Strategy

Define the following partition counts for local scaling demos:

- `stream.chat.messages`: 3 partitions
- `stream.sentiment.events`: 3 partitions
- `stream.sponsor.detections`: 3 partitions

Three partitions gives enough range to show consumer group distribution across two or three replicas without overloading a single-node local cluster.

### Key Strategy

Key Kafka records by `streamer` in all producer services. This ensures all messages for a given streamer land on the same partition, preserving temporal ordering for per-streamer analytics while allowing parallel processing across streamers.

### Consumer Scaling Target

Scale `sentiment-service` as the primary scaling demonstration target. It is the highest-throughput consumer in the platform because every chat message flows through it. Demonstrate scaling from one replica to two or three replicas and confirm partition reassignment.

If capacity allows, also scale `video-service` to show the same pattern for the sponsor detection pipeline.

### Lag Visibility

Use **Kafka Exporter** (the Strimzi-bundled or standalone `danielqsj/kafka-exporter`) to expose consumer group lag metrics to Prometheus. Add Grafana panels for:

- consumer lag per group and topic
- messages produced per second per topic
- messages consumed per second per consumer group
- rebalance event count if available from the exporter

### Monitoring Scope

The Kafka observability additions for Sprint 10 are additive. Do not remove or break the existing Sprint 9 Grafana dashboard. Add a new `StreamSense - Kafka` dashboard provisioned via ConfigMap.

## Sprint 10 Deliverables

### 1. Strimzi Installation And Kafka Cluster

- install Strimzi operator in the `streamsense` namespace or a dedicated operator namespace
- add a `Kafka` custom resource definition for a minimal single-broker cluster suitable for local `kind` usage
- document the install commands and any version pins

### 2. In-Cluster Topic Definitions

- define `KafkaTopic` resources for:
  - `stream.chat.messages` (3 partitions, replication factor 1)
  - `stream.sentiment.events` (3 partitions, replication factor 1)
  - `stream.sponsor.detections` (3 partitions, replication factor 1)
- keep replication factor 1 to stay within single-broker local constraints
- replace or supplement the existing `kafka-topics-init` Job where it no longer applies in the Strimzi model

### 3. Service Config Updates For In-Cluster Kafka

- update Kubernetes ConfigMaps (or `config-server/config-repo/` YAML files mounted in-cluster) so all Spring services resolve the correct in-cluster Kafka bootstrap address
- the in-cluster Kafka bootstrap service name should follow the Strimzi naming convention
- do not change Docker Compose config — keep `localhost:29092` or the existing Compose Kafka address intact for the Docker dev path
- verify each consuming service can connect on startup: `chat-service`, `sentiment-service`, `video-service`, `api-gateway`

### 4. Producer Key Updates

- update Kafka producer configuration in `chat-service` and `video-service` to key messages by `streamer`
- add a `StringSerializer` key serializer if not already present
- keep existing message value serialization unchanged

### 5. Consumer Scaling Demonstration

- confirm `sentiment-service` consumer group name and partition assignment with one replica
- scale `sentiment-service` to two or three replicas
- verify via `kubectl logs` or Kafka consumer group tooling that partitions distribute across replicas
- document the scale-up and scale-down commands in the runbook

### 6. Kafka Exporter And Lag Metrics

- deploy Kafka Exporter targeting the in-cluster Kafka cluster
- configure Prometheus to scrape the exporter
- add a `StreamSense - Kafka` Grafana dashboard provisioned via ConfigMap with panels for:
  - consumer lag by group and topic
  - produce rate per topic
  - consume rate per consumer group

### 7. Updated Runbook And Command History

- update `docs/kubernetes-kind.md` with Strimzi install steps, Kafka startup validation, and scaling commands
- add a Kafka-specific troubleshooting section covering:
  - broker not ready
  - consumer group stuck in rebalance
  - topic not created
  - service DNS resolution failure for Kafka bootstrap address
- add `opencodeCommandHistory/2026-04-14-sprint-10-kafka-on-kubernetes.md` on completion

## Required Scope Breakdown

### Phase 1 - Freeze Kafka Strategy And Partition Design

1. Confirm Strimzi as the operator choice and document the version to install.
2. Confirm partition counts for all three topics.
3. Confirm `streamer` as the producer key field.
4. Confirm the in-cluster Kafka bootstrap address pattern (Strimzi naming convention).
5. Confirm that Docker Compose Kafka config is not touched.
6. Confirm the Kafka Exporter approach for lag metrics.

#### Expected end state

- Kafka strategy is locked before any manifest work begins
- no ambiguity about broker address, topic names, or operator choice

### Phase 2 - Install Strimzi And Bootstrap The Kafka Cluster

1. Install the Strimzi operator (via kubectl apply from the Strimzi release bundle or Helm chart).
2. Create a minimal `Kafka` custom resource in the `streamsense` namespace with:
   - one broker
   - one Zookeeper node (or KRaft if Strimzi version supports it cleanly)
   - appropriate resource limits for local `kind`
3. Wait for the Kafka cluster to reach `Ready`.
4. Validate the broker service DNS name is resolvable from a test pod.

#### Expected end state

- a working Kafka cluster runs in Kubernetes
- its bootstrap address is confirmed and matches the name used in service config

### Phase 3 - Define Topics And Update Producer Config

1. Apply `KafkaTopic` CRDs for all three topics.
2. Verify topics are created via Kafka Exporter metadata or a topic-listing job.
3. Update `config-server/config-repo/` YAML entries (or the relevant in-cluster ConfigMaps) to point `spring.kafka.bootstrap-servers` at the Strimzi Kafka service.
4. Update Kafka producer `key-serializer` in `chat-service` and `video-service` to `StringSerializer`.
5. Add `streamer` as the explicit key in the Kafka `ProducerRecord` calls.
6. Redeploy affected services and verify they connect to in-cluster Kafka without errors.

#### Expected end state

- topics exist with the correct partition counts
- all Spring producers send records to in-cluster Kafka with streamer-keyed messages

### Phase 4 - Validate Consumer Baseline With One Replica

1. Confirm `sentiment-service`, `video-service`, and `api-gateway` Kafka consumers connect to the in-cluster broker.
2. Ingest a small batch of chat messages and frames through the gateway.
3. Verify sentiment events and sponsor detection events are produced and consumed in-cluster.
4. Check `kubectl logs` for consumer group assignment and confirm partition ownership matches expected behavior with one replica.

#### Expected end state

- the end-to-end streaming path works in Kubernetes using in-cluster Kafka
- this is the functional baseline before adding replicas

### Phase 5 - Scale Consumers And Demonstrate Partition Distribution

1. Scale `sentiment-service` to two replicas: `kubectl scale deployment sentiment-service --replicas=2 -n streamsense`
2. Verify Kafka rebalances and distributes the three `stream.chat.messages` partitions across both replicas.
3. Ingest traffic and confirm both replicas process messages.
4. Scale back to one replica and confirm Kafka rebalances back.
5. If cluster resources allow, repeat with three replicas to show full partition coverage.
6. Document the scale commands and expected partition assignment for the runbook.

#### Expected end state

- consumer scaling is demonstrable with documented commands
- partition assignment behavior is confirmed and explained in the runbook

### Phase 6 - Kafka Exporter And Grafana Dashboard

1. Deploy Kafka Exporter targeting the in-cluster Strimzi Kafka service.
2. Add a Prometheus scrape job for the Kafka Exporter pod.
3. Create a `StreamSense - Kafka` Grafana dashboard ConfigMap with panels for:
   - `kafka_consumergroup_lag` by group, topic, and partition
   - `kafka_topic_partition_current_offset` rate as a produce metric
   - `kafka_consumergroup_current_offset` rate as a consume metric
4. Mount the new dashboard ConfigMap into the Grafana Deployment alongside existing dashboards.
5. Verify the dashboard shows real lag values during and after a traffic injection.

#### Expected end state

- consumer lag is visible in Grafana
- produce and consume rates move when traffic flows
- the observability story for the Kafka layer is as solid as the application layer already established in Sprint 9

### Phase 7 - Runbook, Troubleshooting, And Command History

1. Update `docs/kubernetes-kind.md`:
   - add Strimzi install prerequisites
   - add Kafka startup validation steps
   - add scaling demo commands
   - add Kafka Exporter and dashboard access notes
2. Add a Kafka troubleshooting section covering the failure modes listed in the deliverables.
3. Verify the full Sprint 10 Kubernetes workflow is reproducible from scratch:
   - cluster creation
   - Strimzi install
   - manifest apply
   - topic creation
   - end-to-end application path
   - scaling demonstration
4. Write `opencodeCommandHistory/2026-04-14-sprint-10-kafka-on-kubernetes.md`.

#### Expected end state

- a new contributor can reproduce the Sprint 10 Kubernetes deployment from documented steps alone
- the command history reflects what was actually built

## Testing Requirements

Sprint 10 testing is focused on deployment correctness and the async pipeline's behavior at scale, not new application logic.

Required verification:

- Kafka broker reaches `Ready` state in the `streamsense` namespace
- all three topics exist with correct partition counts
- a chat ingest through the gateway triggers sentiment events visible in `kubectl logs` for `sentiment-service`
- scaling `sentiment-service` to multiple replicas results in visible partition reassignment
- Kafka Exporter exposes lag metrics that Prometheus can scrape
- Grafana Kafka dashboard displays lag and rate values under traffic

Keep existing local integration tests unchanged. They use Embedded Kafka or Testcontainers and should not be coupled to the in-cluster Kafka address.

If time allows, add at least one partitioning correctness or per-streamer ordering test to the `chat-service` or `sentiment-service` test suite to document the expected keying behavior.

## Observability Requirements

Sprint 10 observability is complete only when:

- Kafka Exporter is running and Prometheus scrapes it successfully
- consumer lag metrics appear for all three consumer groups (`chat-service` if applicable, `sentiment-service`, `video-service`, `api-gateway`)
- the Kafka Grafana dashboard is provisioned automatically, not manually clicked into existence
- the dashboard shows meaningful values during a traffic injection run

## Demo Script

Sprint 10 should end with a demonstrable Kafka scaling story:

1. Show the `streamsense` namespace with Kafka pods running alongside application services.
2. Ingest a batch of chat messages and frames through the gateway.
3. Open Grafana and show the Kafka dashboard — lag drops as consumers process the batch.
4. Scale `sentiment-service` to two or three replicas.
5. Show Kubernetes reassigning partitions across replicas in logs or via consumer group tooling.
6. Show lag drops faster with multiple consumers.
7. Scale back down and show rebalancing again.
8. Open Zipkin to confirm traces still include in-cluster Kafka-consuming services.

## Definition Of Done

Sprint 10 is complete when:

- Kafka runs in the `streamsense` namespace with Strimzi or documented equivalent
- all three topics exist with three partitions each
- all Spring services connect to in-cluster Kafka and process events end-to-end
- messages are keyed by `streamer`
- `sentiment-service` can be scaled and partition assignment distributes correctly
- consumer lag and throughput are visible in a provisioned Grafana dashboard
- the Kubernetes runbook covers Kafka installation, validation, scaling, and common failures
- Docker Compose remains unchanged and still works independently

## Risks To Watch

- Strimzi operator resource usage overwhelming a single-node `kind` cluster — mitigate by using minimal broker and Zookeeper resource limits in the `Kafka` CR
- Strimzi bootstrap service name not matching what Spring services expect — freeze the exact DNS name before updating config
- consumer group lag exporter incompatibility with the chosen Strimzi Kafka version — pin Kafka Exporter and Strimzi versions together
- per-streamer key change breaking existing embedded-Kafka integration tests that do not set a key — audit tests before merging
- scaling demo producing misleading results on a resource-constrained local cluster — document hardware context and note that results reflect local single-node behavior
- accidentally changing Docker Compose Kafka config while updating Kubernetes config paths — keep the two config paths clearly separated in `config-server/config-repo/` or via Kubernetes-only ConfigMap overrides
