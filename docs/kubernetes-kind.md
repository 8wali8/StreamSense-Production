# Local Kubernetes Runbook

## Purpose

This runbook brings StreamSense up on a local `kind` cluster using the same prebuilt-JAR Docker image workflow already used by Docker Compose.

The cluster keeps the full repo architecture intact:

- `eureka-server` stays in-cluster
- `config-server` stays in native mode
- Spring services fetch centralized config from `config-server`
- Kafka runs as a single-node local broker with three partitions per topic
- `kafka-exporter` scrapes consumer lag and topic metrics for Prometheus
- ingress exposes `api-gateway`, Grafana, and Zipkin

This is a local-first Kubernetes workflow. It is not the final cloud production shape.

## Prerequisites

Install:

- Docker Desktop or Docker Engine
- `kubectl`
- `kind`
- Java 21
- Maven

On macOS with Homebrew:

```bash
brew install kind kubectl
```

## Files

Important Kubernetes assets:

- `k8s/namespace.yaml`
- `k8s/kustomization.yaml`
- `k8s/kind/cluster.yaml`
- `k8s/config/`
- `k8s/platform/`
- `k8s/apps/`
- `k8s/monitoring/`
- `k8s/ingress/`

## 1. Build Java JARs

The Java service Dockerfiles still copy `target/*.jar`, so build those artifacts first:

```bash
make package
```

## 2. Build Local Images

Build the images used by the Kubernetes manifests:

```bash
docker build -t streamsense/eureka-server:sprint9 ./eureka-server
docker build -t streamsense/config-server:sprint9 ./config-server
docker build -t streamsense/chat-service:sprint9 ./chat-service
docker build -t streamsense/sentiment-service:sprint9 ./sentiment-service
docker build -t streamsense/video-service:sprint9 ./video-service
docker build -t streamsense/recommendation-service:sprint9 ./recommendation-service
docker build -t streamsense/api-gateway:sprint9 ./api-gateway
docker build -t streamsense/ml-engine:sprint9 ./ml-engine
```

## 3. Create The `kind` Cluster

Create the cluster with ingress-friendly host port mappings:

```bash
kind create cluster --name streamsense --config k8s/kind/cluster.yaml
```

Confirm the context:

```bash
kubectl config current-context
```

Expected value:

```text
kind-streamsense
```

## 4. Load Local Images Into `kind`

```bash
kind load docker-image \
  streamsense/eureka-server:sprint9 \
  streamsense/config-server:sprint9 \
  streamsense/chat-service:sprint9 \
  streamsense/sentiment-service:sprint9 \
  streamsense/video-service:sprint9 \
  streamsense/recommendation-service:sprint9 \
  streamsense/api-gateway:sprint9 \
  streamsense/ml-engine:sprint9 \
  --name streamsense
```

## 5. Install Ingress NGINX

For `kind`, use the upstream ingress-nginx manifest:

```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
kubectl wait --namespace ingress-nginx --for=condition=Available deployment/ingress-nginx-controller --timeout=300s
```

## 6. Validate And Apply StreamSense

Create the git-ignored secret env file that kustomize turns into the `streamsense-secrets-<hash>` Secret. `make secrets` writes it with random values (the same ones Compose uses on this machine); to choose values yourself, copy `k8s/secrets/streamsense.env.example` and replace every placeholder:

```bash
make secrets
```

kustomize appends a hash of the contents to the Secret name and rewrites every `secretKeyRef` to match, so changing a value and running `kubectl apply -k k8s` again rolls the pods that use it.

Every credential in the manifests (Postgres, MinIO, Grafana admin, the gateway HMAC secret) comes from that Secret through `secretKeyRef`; nothing is inlined in YAML.

Render validation:

```bash
kubectl kustomize . >/tmp/streamsense-k8s-rendered.yaml
```

Apply the stack:

```bash
kubectl apply -k .
```

Apply from the repository root, not from `k8s/`: the root `kustomization.yaml` generates the config-server ConfigMap from `config-server/config-repo/*.yml`, which `k8s/` cannot reference on its own. Every workload now carries resource requests and limits, a non-root `securityContext`, and split liveness and readiness probes; Postgres, MinIO, and the ml-engine model caches use PersistentVolumeClaims (kind's default StorageClass provisions them). The namespace enforces the `baseline` Pod Security Standard and warns on anything below `restricted`.

If ingress creation races the admission webhook on a fresh cluster, re-apply ingress after the controller is ready:

```bash
kubectl apply -f k8s/ingress/streamsense-ingress.yaml
```

### Upgrading a cluster created before this layout

`kubectl apply -k .` on a cluster that was created from the earlier manifests needs three things done first; a plain apply is not enough.

1. **Postgres and MinIO data moves from `emptyDir` to PersistentVolumeClaims, and the switch is destructive.** The `Recreate` rollout deletes the old pod together with its `emptyDir` before the new pod starts on a blank claim. If the history matters, export it first (`kubectl -n streamsense exec deploy/postgres -- pg_dumpall -U streamsense > backup.sql`, and `mc mirror` the MinIO bucket) and restore it into the new pods; on a throwaway kind cluster, accept the loss.
2. **The `kafka-topics-init` Job's pod template changed (security context, resources, TTL) and a started Job's template is immutable.** Delete the finished Job before applying: `kubectl -n streamsense delete job kafka-topics-init --ignore-not-found`.
3. **Kafka is a StatefulSet in KRaft mode and ZooKeeper is gone, but `kubectl apply` never deletes what a manifest no longer lists.** The old `Deployment/kafka` shares the `app: kafka` label with the new StatefulSet, so the `kafka` Service would route clients to two unrelated brokers. Remove the old workloads first: `kubectl -n streamsense delete deployment kafka zookeeper --ignore-not-found` and `kubectl -n streamsense delete service zookeeper --ignore-not-found`. A ZooKeeper-era Kafka data directory is not migrated; the StatefulSet formats a fresh log on its own claim.
4. **Apply from the repository root.** The config-server ConfigMap is now generated by the root `kustomization.yaml`; `kubectl apply -k k8s` leaves config-server waiting for a ConfigMap that no longer exists in the base.

The simplest upgrade for a kind cluster is `kubectl delete namespace streamsense` followed by `kubectl apply -k .`.

## 7. Wait For Readiness

Check all pods in the StreamSense namespace:

```bash
kubectl get pods -n streamsense -o wide
```

Expected steady state:

- all Deployments show `1/1 Running`
- `kafka-topics-init` shows `Completed`

Note:

- the heavier Spring services can take several minutes to finish Config Server fetch, Flyway, JPA, and Kafka startup on a single-node `kind` cluster
- use `kubectl get pods -n streamsense -w` and give the stack time to settle before assuming a failed deployment

## 8. Local Access

Ingress hosts are:

- `gateway.streamsense.local`
- `grafana.streamsense.local`
- `zipkin.streamsense.local`

If you do not want to edit `/etc/hosts`, use `curl` with an explicit `Host` header against `127.0.0.1`.

If you want browser access by hostname, add:

```text
127.0.0.1 gateway.streamsense.local grafana.streamsense.local zipkin.streamsense.local
```

## 9. Verification

### Gateway health through ingress

```bash
curl -s -H 'Host: gateway.streamsense.local' http://127.0.0.1/actuator/health
```

### GraphQL health through ingress

```bash
curl -s \
  -H 'Host: gateway.streamsense.local' \
  -H 'Content-Type: application/json' \
  -d '{"query":"query { health }"}' \
  http://127.0.0.1/graphql
```

Expected response:

```json
{"data":{"health":"ok"}}
```

### Chat ingest through gateway ingress

```bash
curl -s \
  -H 'Host: gateway.streamsense.local' \
  -H 'Content-Type: application/json' \
  -d '{"streamer":"k8s-sprint9","user":"tester","message":"hello from kind","timestamp":1710000000000}' \
  http://127.0.0.1/api/chat/ingest
```

Expected response shape:

```json
{"eventId":"..."}
```

### Grafana health through ingress

```bash
curl -s -H 'Host: grafana.streamsense.local' http://127.0.0.1/api/health
```

### Zipkin health through ingress

```bash
curl -s -H 'Host: zipkin.streamsense.local' http://127.0.0.1/health
```

### Prometheus query for chat ingest metric

Port-forward Prometheus locally:

```bash
kubectl port-forward -n streamsense svc/prometheus 9090:9090
```

Then query:

```bash
curl -s 'http://127.0.0.1:9090/api/v1/query?query=streamsense_chat_ingest_total'
```

### Zipkin service list

After gateway traffic, verify that traces arrived:

```bash
curl -s -H 'Host: zipkin.streamsense.local' http://127.0.0.1/api/v2/services
```

## Gateway Demo Toggles

The local `kind` manifests keep the same defaults as Compose:

- `STREAMSENSE_GATEWAY_AUTH_ENABLED=false`
- `STREAMSENSE_GATEWAY_RATE_LIMIT_ENABLED=true`

For a backend-focused benchmark in `kind`, patch the gateway deployment temporarily:

```bash
kubectl -n streamsense set env deployment/api-gateway STREAMSENSE_GATEWAY_RATE_LIMIT_ENABLED=false
kubectl -n streamsense rollout status deployment/api-gateway
```

Restore the normal demo policy afterward:

```bash
kubectl -n streamsense set env deployment/api-gateway STREAMSENSE_GATEWAY_RATE_LIMIT_ENABLED=true
kubectl -n streamsense rollout status deployment/api-gateway
```

## Troubleshooting

### Pods are stuck in `ImagePullBackOff`

The local images were not loaded into `kind`.

Fix:

```bash
kind load docker-image <image> --name streamsense
```

### `config-server` cannot see the native config repo

Check the mounted ConfigMap and env:

```bash
kubectl describe pod -n streamsense -l app=config-server
kubectl get configmap config-server-native-repo -n streamsense -o yaml
```

The deployment should mount the config files at `/config-repo` and use:

```text
CONFIG_SERVER_NATIVE_SEARCH_LOCATIONS=file:/config-repo
```

### Kafka crashes immediately

The Confluent image is sensitive to Kubernetes service-link environment variables. The Sprint 9 manifests disable service links on the Kafka pod template so only the intended broker env vars are present.

Check:

```bash
kubectl logs statefulset/kafka -n streamsense
```

### Ingress apply fails with webhook connection refused

This usually means ingress-nginx started but the admission webhook was not ready yet.

Fix:

```bash
kubectl wait --namespace ingress-nginx --for=condition=Available deployment/ingress-nginx-controller --timeout=300s
kubectl apply -f k8s/ingress/streamsense-ingress.yaml
```

### A Spring service is stuck in init containers

Describe the pod to see which dependency is still unavailable:

```bash
kubectl describe pod <pod-name> -n streamsense
```

Then inspect the dependency directly:

```bash
kubectl get pods -n streamsense
kubectl logs deployment/<dependency> -n streamsense
```

## 10. Kafka Consumer Lag Dashboard

Kafka Exporter runs in-cluster and exposes consumer group lag metrics to Prometheus. Grafana provisions the `StreamSense - Kafka` dashboard automatically.

To access the dashboard, open Grafana at `http://grafana.streamsense.local` (or via port-forward) and select **StreamSense - Kafka** from the dashboard list.

To verify the exporter is up directly:

```bash
kubectl port-forward -n streamsense svc/kafka-exporter 9308:9308
curl -s http://127.0.0.1:9308/metrics | grep kafka_consumergroup_lag | head -10
```

To check consumer group lag via Prometheus:

```bash
kubectl port-forward -n streamsense svc/prometheus 9090:9090
curl -s 'http://127.0.0.1:9090/api/v1/query?query=kafka_consumergroup_lag' | python3 -m json.tool
```

## 11. Consumer Scaling Demonstration

`sentiment-service` uses consumer group `sentiment-service` and subscribes to `stream.chat.messages` which has three partitions. Scaling the deployment demonstrates Kafka partition rebalancing.

### Check current partition assignment (one replica)

```bash
kubectl exec -n streamsense statefulset/kafka -- \
  kafka-consumer-groups --bootstrap-server kafka:9092 \
  --describe --group sentiment-service
```

Expected output shows all three partitions owned by a single consumer instance.

### Scale to two replicas

```bash
kubectl scale deployment sentiment-service --replicas=2 -n streamsense
kubectl rollout status deployment/sentiment-service -n streamsense
```

### Verify partition rebalance

Wait about 30 seconds for Kafka to rebalance, then:

```bash
kubectl exec -n streamsense statefulset/kafka -- \
  kafka-consumer-groups --bootstrap-server kafka:9092 \
  --describe --group sentiment-service
```

Expected output shows partitions split across two consumer IDs (approximately 1–2 partitions each).

### Inject traffic to observe lag movement

```bash
for i in $(seq 1 10); do
  curl -s \
    -H 'Host: gateway.streamsense.local' \
    -H 'Content-Type: application/json' \
    -d "{\"streamer\":\"scale-demo\",\"user\":\"u${i}\",\"message\":\"message ${i}\",\"timestamp\":$((1710000000000 + i))}" \
    http://127.0.0.1/api/chat/ingest
done
```

Open the Kafka dashboard in Grafana and observe the **Consumer Lag by Group and Topic** panel: lag rises briefly as messages are produced, then drops as consumers process them.

### Scale back to one replica

```bash
kubectl scale deployment sentiment-service --replicas=1 -n streamsense
kubectl rollout status deployment/sentiment-service -n streamsense
```

Run the consumer group describe again to confirm all partitions return to a single consumer.

## Troubleshooting - Kafka

### Kafka broker not ready

`kafka-topics-init` Job fails because the broker is not yet available.

Check broker logs:

```bash
kubectl logs statefulset/kafka -n streamsense
```

Common cause: the Confluent image picked up a Kubernetes service-link env var (e.g., `KAFKA_PORT`). The Kafka pod template sets `enableServiceLinks: false` to prevent this. If a manifest change accidentally re-enables service links, disable them again.

### Consumer group stuck in rebalance

Symptoms: consumer group describe shows `REBALANCING` state for more than a couple of minutes, or lag grows continuously.

Check consumer service logs:

```bash
kubectl logs deployment/sentiment-service -n streamsense
```

Look for heartbeat timeout or session timeout errors. If pods are restarting frequently, probe intervals or JVM startup time may be causing liveness failures before the consumer fully joins the group.

### Topic not created

`kafka-topics-init` Job may have completed before Kafka was actually accepting connections.

Check Job logs:

```bash
kubectl logs job/kafka-topics-init -n streamsense
```

If the Job failed, delete and re-apply it:

```bash
kubectl delete job kafka-topics-init -n streamsense
kubectl apply -f k8s/platform/kafka.yaml  # StatefulSet + PVC; delete the PVC too if you want a clean log directory
```

Verify topics exist:

```bash
kubectl exec -n streamsense statefulset/kafka -- \
  kafka-topics --bootstrap-server kafka:9092 --list
```

Expected topics:

```text
stream.chat.messages
stream.chat.messages.dlt
stream.sentiment.events
stream.sponsor.detections
stream.video.frames
```

### Kafka Exporter shows no metrics

Check if the exporter pod is ready:

```bash
kubectl get pods -n streamsense -l app=kafka-exporter
kubectl logs deployment/kafka-exporter -n streamsense
```

The exporter waits for Kafka via an init container. If the init container is stuck, Kafka may not be ready yet. Wait and retry.

If the exporter is running but Prometheus shows no `kafka_consumergroup_*` metrics, verify the scrape job:

```bash
kubectl port-forward -n streamsense svc/prometheus 9090:9090
# then open http://127.0.0.1:9090/targets and check kafka-exporter target status
```

### Service DNS resolution failure for Kafka bootstrap address

Spring services should use `kafka:9092` as the bootstrap address. This is set via `KAFKA_BOOTSTRAP_SERVERS=kafka:9092` in each app Deployment.

If a service fails to connect at startup:

```bash
kubectl exec -n streamsense deployment/chat-service -- \
  wget -qO- http://kafka:9092 || echo "port check only"
```

Or check DNS resolution directly:

```bash
kubectl run dns-test --image=busybox:1.36 --restart=Never -n streamsense -- \
  nslookup kafka
kubectl delete pod dns-test -n streamsense
```

## Cleanup

Delete the application stack:

```bash
kubectl delete namespace streamsense
```

Delete the cluster:

```bash
kind delete cluster --name streamsense
```
