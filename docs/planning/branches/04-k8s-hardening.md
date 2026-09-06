# hardening/04-k8s-hardening

Priority 4 from `docs/planning/production-hardening.md`: every Kubernetes container is bounded and hardened, liveness and readiness are separate, stateful data survives restarts, and Kubernetes reads config from the same files Compose does. Stacked on `hardening/03-client-timeouts` (which carries the config-repo edits this branch's generator now serves). It does not include the image pins from `hardening/02b-image-pinning`; the two touch different lines of the same manifests and merge cleanly.

## What changed

**Every container has `resources` and a non-root `securityContext`.** All 20 main containers, the 34 busybox init containers, and the topics Job now declare CPU and memory requests, a memory limit, `runAsNonRoot`, an explicit `runAsUser`, `allowPrivilegeEscalation: false`, `capabilities.drop: ["ALL"]`, and `seccompProfile: RuntimeDefault`. UIDs follow each image's own user: 999 for postgres and redis, 1000 for the Confluent images, minio, and zipkin, 472 for grafana, 65534 for prometheus, kafka-exporter, and busybox, and 10001 for the StreamSense images, which have no built-in user. Java containers get `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0` so the heap tracks the new limit instead of the JVM's 25 % default; the Python containers get `HOME=/tmp` so any library cache write lands somewhere writable. Postgres, MinIO, and ml-engine pods set `fsGroup` so their volumes are writable by the non-root user, and postgres sets `PGDATA` to a subdirectory so the official image accepts a freshly provisioned volume.

Limits are memory only. CPU limits cause throttling that hurts JVM start-up and inference latency far more than they protect a single-tenant cluster; requests still drive scheduling.

**Liveness and readiness are split for the Spring services.** `management.endpoint.health.probes.enabled: true` in the shared config-repo `application.yml` (and in the eureka-server and config-server bootstrap files, which do not use config-repo) exposes `/actuator/health/liveness` and `/readiness`. All eight Java Deployments point readiness at `/readiness`, and liveness and startup at `/liveness`. sentiment-service and video-service include `db` and `redis` in their readiness group, analytics-service includes `db`; liveness never includes a dependency, so a Postgres or Redis outage takes a pod out of rotation instead of restarting it in a loop. ml-engine gains a startup probe (up to 10 minutes) because model loading can take that long on first run.

**Stateful data moves to PersistentVolumeClaims.** `k8s/platform/storage.yaml` declares `postgres-data` (5 Gi), `minio-data` (10 Gi), and one claim per ml-engine model cache: `ml-engine-whisper-cache` (2 Gi), `ml-engine-relevance-cache` (1 Gi), `ml-engine-sentiment-cache` (2 Gi), and `ml-engine-sam-cache` (4 Gi), matching the four named volumes Compose mounts. Under the non-root UID every cache directory the backends write to must be a mounted claim; without the sentiment one, Transformers could not create its cache and the analyzer silently fell back to the lexical model. Postgres, MinIO, and ml-engine mount them instead of `emptyDir` and use `strategy: Recreate`, which a single-replica Deployment with a ReadWriteOnce claim needs. kind's default StorageClass provisions the claims dynamically.

**The config-server ConfigMap is generated, not copied.** A new root `kustomization.yaml` includes `k8s/` and runs `configMapGenerator` over `config-server/config-repo/*.yml`. The 552-line hand-mirrored `k8s/config/config-server-config-repo.yaml` is deleted. The generated name carries a content hash and kustomize rewrites the reference in `k8s/platform/config-server.yaml`, so a config change rolls config-server automatically. The entry point becomes `kubectl apply -k .` from the repository root; kustomize cannot reference files above its own directory, which is why the root file exists.

**The namespace enforces Pod Security Standards**: `enforce: baseline`, `warn: restricted`, `audit: restricted`. Every pod in this branch already satisfies `restricted` except for the read-only root filesystem, which is listed below.

**Upgrade path**: the PVC switch is destructive for data that lived in `emptyDir`, and a started Job's pod template is immutable, so `docs/kubernetes-kind.md` gains an upgrade section (export Postgres and MinIO first or accept the loss, delete the finished `kafka-topics-init` Job, apply from the root). **Config-repo is now tested**: `config-server`'s `ConfigRepoYamlTest` parses every `config-repo/*.yml` with duplicate keys rejected, because the first cut of this branch added a second `management.endpoint` mapping to `analytics-service.yml`, which config-server refuses to serve (HTTP 500) and which only showed up when the whole stack booted in CI. **Housekeeping**: the topics Job gets `ttlSecondsAfterFinished: 600`; CI, `docs/kubernetes-kind.md`, CLAUDE.md, AGENTS.md, and `docs/current-state.md` describe the root entry point and the new rules.

## Deliberately left alone

- `readOnlyRootFilesystem` is not set. The Confluent images template configuration into `/etc/kafka` at start, Grafana writes its SQLite database, and the JVM and Python images write to `/tmp`. Setting it needs per-image `emptyDir` mounts for each writable path; that is its own change and `kube-linter` will keep flagging it (54 findings, all this check) until it lands.
- The busybox init-container wait loops stay. Removing them would touch the same lines `hardening/02b-image-pinning` changed; with probes and the config-client retry from branch 03 they are redundant and can go in a later hygiene branch.
- Kafka and ZooKeeper keep `emptyDir`; branch 05 replaces both with a KRaft StatefulSet on a PVC.
- No NetworkPolicy or PodDisruptionBudget yet; replicas are 1 everywhere and a default-deny policy needs the KRaft topology settled first.
- Resource numbers are starting points sized for a laptop kind cluster, not measurements. Prometheus already scrapes every Spring service, so `container_memory_working_set_bytes` against these limits is the next thing to look at.

## Verification

| Check | Command | Result |
|---|---|---|
| Renders from the root | `kubectl kustomize .` | OK, 3835 lines, 52 resources |
| Schema validation | `kubeconform -strict -kubernetes-version 1.34.1` on the rendered output | 52 valid, 0 invalid |
| Policy lint | `kube-linter lint` on the rendered output | 54 × `no-read-only-root-fs` (deliberate, above); 4 × `latest-tag` (zipkin, grafana, prometheus, kafka-exporter, pinned in branch 02b); nothing else |
| Generated ConfigMap | grep of the render | `config-server-native-repo-<hash>` with all 8 config-repo files, referenced by the config-server Deployment |
| Probe split | grep of the render | every Spring readiness probe on `/actuator/health/readiness`, liveness and startup on `/liveness` |
| Config-repo files parse strictly | `ConfigRepoYamlTest` (8 dynamic cases, one per config-repo file) in `maven:3.9-eclipse-temurin-21` | all 8 pass after the `analytics-service.yml` fix; the pre-existing `contextLoads` test needs Mockito's inline mock maker to self-attach, which the container blocks, so CI is the authority for that one |
| eureka-server, config-server tests (bootstrap config changed) | `mvn -B -ntp clean test` in `maven:3.9-eclipse-temurin-21` | both pass (1 context test each) |

The other Java services' runtime config changed (probes and readiness groups) but their code did not; their test configs shadow the bootstrap file, so their suites were not rerun here.

## Manual checks for the reviewer

1. On kind (`docs/kubernetes-kind.md`): create `k8s/secrets/streamsense.env`, `kind load` the app images, `kubectl apply -k .`. `kubectl -n streamsense get pods` should reach Running/Ready for every workload; `kubectl -n streamsense get pvc` shows four Bound claims.
2. `kubectl -n streamsense get events --field-selector reason=FailedCreate` and `kubectl -n streamsense get events | grep -i "violates PodSecurity"` are both empty.
3. `kubectl -n streamsense exec deploy/sentiment-service -- id` prints uid 10001; `kubectl -n streamsense exec deploy/postgres -- id` prints uid 999.
4. `kubectl -n streamsense port-forward svc/sentiment-service 8083:8083` then `curl localhost:8083/actuator/health/readiness` returns `{"status":"UP"}` with `db` and `redis` components; `kubectl -n streamsense scale deploy/redis --replicas=0` turns readiness DOWN while liveness stays UP and the pod is not restarted.
5. Delete the postgres pod: after it returns, previously ingested sentiment history is still there.
6. Edit any value in `config-server/config-repo/chat-service.yml`, `kubectl apply -k .`, and confirm the config-server pod rolls (new ConfigMap hash).
7. Watch memory: `kubectl -n streamsense top pods` after ten minutes of replay; any pod near its limit needs its number raised in `k8s/apps/<service>.yaml`.

## Follow-ups (not in this branch)

- Branch 05: KRaft Kafka on a StatefulSet and PVC; drop ZooKeeper.
- Read-only root filesystems with explicit writable `emptyDir` mounts per image.
- Remove the busybox init containers once 02b has merged.
- NetworkPolicy default-deny with per-service allows, and PDBs once anything runs more than one replica.
