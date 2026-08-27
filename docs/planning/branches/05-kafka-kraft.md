# hardening/05-kafka-kraft

Priority 5 from `docs/planning/production-hardening.md`: Kafka runs in KRaft mode with no ZooKeeper, on persistent storage, in both Compose and Kubernetes. Kafka 4.0 removed ZooKeeper mode entirely; staying on it blocks every future broker upgrade.

**Integration point.** This branch is where the earlier branches meet: it starts from `hardening/04-k8s-hardening` (which contains 01 and 03) and merges `hardening/02a-ci-pinning` and `hardening/02c-python-packaging` (which contains 02b). The two merge commits resolve the expected conflicts: CLAUDE.md's CI-parity paragraph (02a and 04 both extended it) and the Kubernetes manifests, where 02b changed every `image:` line and 04 inserted `securityContext` and `resources` right after it. The resolution keeps the 04 side and re-applies the 02b digest pins, and keeps the SHA-pinned workflow from 02a with the uv steps from 02c. Every branch after this one stacks linearly on 05, so the reviewer can merge 01 through 05 in order and the later PRs will apply cleanly.

## What changed

**Compose**: the `zookeeper` service is gone. `kafka` runs as a combined broker and controller (`KAFKA_PROCESS_ROLES: broker,controller`, `KAFKA_NODE_ID: 1`, `KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:29093`) with a third, never-advertised `CONTROLLER` listener on 29093. The `INTERNAL` (`kafka:9092`) and `EXTERNAL` (`localhost:29092`) listeners, the replication-factor settings, the healthcheck, and `auto-create-topics=false` are unchanged, so every client keeps its bootstrap address. The log directory moves to a named volume (`kafka-data`), so topics and offsets survive `docker compose down`. `CLUSTER_ID` is a fixed 22-character base64 id derived from the project name; it identifies the log directory and is not a secret. Kafka UI loses its `KAFKA_CLUSTERS_0_ZOOKEEPER` setting.

**Kubernetes**: `k8s/platform/kafka.yaml` is rewritten. The ZooKeeper Service and Deployment and the Kafka `wait-for-zookeeper` init container are gone. Kafka is a `StatefulSet` behind the existing `kafka` ClusterIP Service plus a new headless `kafka-headless` Service that gives the broker the stable name the quorum voter list needs (`1@kafka-0.kafka-headless.streamsense.svc.cluster.local:29093`). Its data is a `volumeClaimTemplate` (5 Gi). The container keeps the hardening from branch 04 (uid 1000, dropped capabilities, seccomp, requests and a 2 Gi limit, `fsGroup` for the claim) and the digest-pinned image from branch 02b. The topics Job is unchanged.

**CI and docs**: the smoke job no longer starts `zookeeper`; CLAUDE.md describes the KRaft layout; the kind runbook notes that a clean Kafka now also means deleting its PVC.

## Deliberately left alone

- `provectuslabs/kafka-ui` stays. Its final release works against a KRaft broker over the bootstrap address; migrating to `kafbat/kafka-ui` is a separate product decision.
- One broker, one controller, replication factor 1. This is the same availability the ZooKeeper setup had. Going to three nodes is a topology change (per-broker StatefulSet ordinals, `min.insync.replicas`, topic replication factors in the init Job) that should be planned with the NetworkPolicy work.
- Kafka's own resource numbers are inherited from branch 04 and unmeasured.

## Verification

| Check | Command | Result |
|---|---|---|
| Compose renders | `docker compose config -q` | OK, and `zookeeper` no longer appears anywhere in the rendered config |
| Kubernetes renders | `kubectl kustomize .` | OK |
| Schema validation | `kubeconform -strict -kubernetes-version 1.34.1` | 51 valid, 0 invalid (one fewer resource: the ZooKeeper Deployment is gone, the headless Service is new, and the ZooKeeper Service is gone) |
| Policy lint | `kube-linter lint` | 52 × `no-read-only-root-fs` only (deliberate, see branch 04); the `latest-tag` findings from 04 are gone now that 02b is merged in |
| Live KRaft boot in Compose | `docker compose up -d kafka kafka-topics-init` on this branch (ports were free), wait for the healthcheck, list topics, describe the quorum, then `docker compose down -v` | broker healthy in about 30 s; the init Job exits 0 and `kafka-topics --list` shows all 13 topics; `kafka-metadata-quorum describe --status` reports `LeaderId: 1`, `CurrentVoters: [1]`, and the fixed cluster id; after `docker compose restart kafka` all 13 topics are still present |
| Workflow lint | `actionlint` | no findings |

## Manual checks for the reviewer

1. `make nuke && make up` (an existing ZooKeeper-era Kafka volume is not migrated; the log directory is formatted fresh on first start). Every service reaches healthy; `docker compose ps` shows no `zookeeper`.
2. `docker compose exec kafka kafka-metadata-quorum --bootstrap-server kafka:9092 describe --status` reports `LeaderId: 1` and no ZooKeeper anywhere in the output.
3. `make replay-smoke` passes, and Kafka UI at `http://localhost:8088` shows the thirteen topics.
4. `docker compose restart kafka`: topics and consumer group offsets are still present afterwards (the named volume).
5. On kind: `kubectl -n streamsense get statefulset kafka` is 1/1, `kubectl -n streamsense get pvc kafka-data-kafka-0` is Bound, and the `kafka-topics-init` Job completes.

## Follow-ups (not in this branch)

- Multi-broker KRaft with separate controllers once availability matters.
- Consider `kafbat/kafka-ui` when Kafka UI is next touched.
