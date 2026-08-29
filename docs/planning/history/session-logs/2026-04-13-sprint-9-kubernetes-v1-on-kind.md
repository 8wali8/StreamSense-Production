# Sprint 9 Command History

## Goal

Implement Sprint 9 end to end:

- add a real local Kubernetes deployment
- keep Config Server native mode
- preserve Eureka and gateway architecture
- make the stack runnable on `kind`
- verify ingress, gateway traffic, metrics, and traces

## Key Work Completed

### Planning

- reviewed `plan.md` Week 9
- added `sprintplans/week-9.md`

### Kubernetes implementation

- created `k8s/namespace.yaml`
- created `k8s/kustomization.yaml`
- created `k8s/kind/cluster.yaml`
- added ConfigMaps for:
  - Config Server native repo content
  - Prometheus config
  - Grafana datasource, provider config, and Sprint 9 dashboard
- added Deployments and Services for:
  - `eureka-server`
  - `config-server`
  - `postgres`
  - `redis`
  - `zookeeper`
  - `kafka`
  - `ml-engine`
  - `chat-service`
  - `sentiment-service`
  - `video-service`
  - `recommendation-service`
  - `api-gateway`
  - `prometheus`
  - `grafana`
  - `zipkin`
- added Kafka topic init job
- added ingress resources for:
  - `gateway.streamsense.local`
  - `grafana.streamsense.local`
  - `zipkin.streamsense.local`

### Important fix discovered during rollout

- Kafka initially crashed in Kubernetes because the Confluent image consumed Kubernetes service-link env vars such as `KAFKA_PORT`
- fixed by setting `enableServiceLinks: false` on the Kafka pod template
- tuned Kubernetes probe behavior after observing that several Spring services legitimately need multiple minutes to finish config fetch, Flyway, JPA, and Kafka startup on single-node `kind`

### Docs

- added `docs/kubernetes-kind.md`
- linked that runbook from `docs/howtorun.md`

## Validation Performed

### Local prerequisites

- confirmed `kubectl` availability
- installed `kind` via Homebrew
- created `kind` cluster with ingress-friendly port mapping
- installed ingress-nginx for `kind`

### Build and image workflow

- ran `make package`
- built local images:
  - `streamsense/eureka-server:sprint9`
  - `streamsense/config-server:sprint9`
  - `streamsense/chat-service:sprint9`
  - `streamsense/sentiment-service:sprint9`
  - `streamsense/video-service:sprint9`
  - `streamsense/recommendation-service:sprint9`
  - `streamsense/api-gateway:sprint9`
  - `streamsense/ml-engine:sprint9`
- loaded those images into `kind`

### Manifest validation

- rendered the Kubernetes set with `kubectl kustomize k8s`
- applied the stack with `kubectl apply -k k8s`
- re-applied ingress after ingress-nginx webhook readiness stabilized

### Runtime verification

- verified all StreamSense pods reached `Running` in namespace `streamsense`
- verified `kafka-topics-init` completed
- verified ingress resources resolved to `localhost`
- deleted and recreated the `streamsense` namespace for a clean final redeploy
- re-verified the stack on that fresh namespace

### End-to-end checks

- gateway health through ingress returned `UP`
- Grafana health through ingress returned healthy JSON
- Zipkin health through ingress returned `UP`
- GraphQL `health` query through gateway ingress returned `ok`
- chat ingest through gateway ingress returned an `eventId`
- Prometheus query for `streamsense_chat_ingest_total` returned a value of `1`
- Zipkin `/api/v2/services` returned traced services including:
  - `api-gateway`
  - `chat-service`
  - `recommendation-service`
  - `sentiment-service`
  - `video-service`

## Notes

- Sprint 9 includes a minimal local Kafka deployment in Kubernetes so the existing services can boot and be tested end to end
- the more complete Kafka-on-Kubernetes and consumer-scaling work remains a Sprint 10 concern
