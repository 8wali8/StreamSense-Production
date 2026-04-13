# Local Kubernetes Runbook

## Purpose

This runbook brings StreamSense up on a local `kind` cluster using the same prebuilt-JAR Docker image workflow already used by Docker Compose.

Sprint 9 keeps the repo architecture intact in Kubernetes:

- `eureka-server` stays in-cluster
- `config-server` stays in native mode
- Spring services still fetch centralized config from `config-server`
- Kafka runs as a minimal single-node local dependency so the existing services can boot and be tested end to end
- ingress exposes `api-gateway`, Grafana, and Zipkin

This is a local-first Kubernetes workflow. It is not the final cloud production shape and it is not the scaling-focused Kafka story planned for Sprint 10.

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

Render validation:

```bash
kubectl kustomize k8s >/tmp/streamsense-k8s-rendered.yaml
```

Apply the stack:

```bash
kubectl apply -k k8s
```

If ingress creation races the admission webhook on a fresh cluster, re-apply ingress after the controller is ready:

```bash
kubectl apply -f k8s/ingress/streamsense-ingress.yaml
```

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
kubectl logs deployment/kafka -n streamsense
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

## Cleanup

Delete the application stack:

```bash
kubectl delete namespace streamsense
```

Delete the cluster:

```bash
kind delete cluster --name streamsense
```
