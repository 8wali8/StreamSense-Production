# Sprint 9 Implementation Plan

## Goal

Make the platform runnable on a local Kubernetes cluster for the first time without breaking the Docker-first workflow that already works.

Sprint 9 should turn the current Docker Compose stack into a real local Kubernetes deployment on `kind` by default, with `minikube` only as a documented fallback if a specific local environment needs it.

The target runtime shape for this sprint is:

`local image build or load -> kind cluster -> namespace-scoped platform services -> api-gateway ingress -> same core StreamSense flows reachable in-cluster`

Sprint 9 is not about perfect cloud production parity and it is not about moving Kafka into Kubernetes yet. It is about proving the application stack can run coherently on Kubernetes with a trustworthy local workflow, clear manifests, and enough observability to validate the deployment.

## Sprint 9 Success Criteria

Sprint 9 is complete only when all of the following are true:

- a local Kubernetes workflow is documented and reproducible on `kind`
- the repository contains first-pass manifests under `k8s/` for the core platform
- a dedicated Kubernetes namespace exists for the stack
- Deployments and Services exist for:
  - `eureka-server`
  - `config-server`
  - `api-gateway`
  - `chat-service`
  - `sentiment-service`
  - `video-service`
  - `recommendation-service`
  - `ml-engine`
  - `postgres`
  - `redis`
  - `prometheus`
  - `grafana`
  - `zipkin`
- ingress or equivalent cluster entrypoints exist for at least:
  - `api-gateway`
  - `grafana`
  - `zipkin`
- `config-server` native mode works in Kubernetes using in-repo config material converted into Kubernetes-managed config files
- the current prebuilt-JAR Docker image approach remains the image source for local Kubernetes deployment
- service-to-service communication works under Kubernetes DNS naming rather than Docker Compose host assumptions
- at least one end-to-end application path is verified in Kubernetes through `api-gateway`
- Prometheus scraping, Grafana provisioning, and Zipkin trace collection still work in-cluster at a baseline level
- the local Kubernetes runbook explains image build, cluster creation, manifest apply order, verification steps, and common failure modes honestly

## Current Starting Point

This plan assumes the repo state after Sprint 8 is:

- the Docker Compose stack is the primary supported runtime and already works for the main platform flows
- the repository has no implemented Kubernetes manifest set under `k8s/`
- the Java services build Docker images from prebuilt JAR artifacts rather than multi-stage image builds
- `config-server` runs in native mode and reads configuration from `config-server/config-repo/`
- service discovery currently relies on `eureka-server` and that architectural role should remain intact for this repository
- `api-gateway` is now the platform edge for routing, GraphQL, auth hooks, and recommendation access
- `sentiment-service`, `video-service`, and `recommendation-service` already depend on downstream services and config that must translate cleanly into Kubernetes DNS names and startup behavior
- Prometheus, Grafana, and Zipkin already exist in Docker Compose and should not be treated as optional later cleanup work
- Kafka still runs only in Docker Compose today and Kubernetes Kafka work is explicitly deferred to Sprint 10

The largest Sprint 9 gap is not application logic. It is deployment shape: the repo can demonstrate the platform in Docker, but not yet as a Kubernetes-deployed system with a repeatable local cluster workflow.

## Important Architecture Note

Sprint 9 must preserve the architecture already established in earlier sprints while translating it to Kubernetes:

- keep `api-gateway` as the single platform entry point for application traffic
- keep history queries service-owned
- keep Kafka as the event backbone, but do not let Kafka-on-Kubernetes become a Sprint 9 blocker
- keep `config-server` in native mode backed by repository-managed configuration
- keep `eureka-server` present so the repo still demonstrates the intended Spring service discovery architecture
- keep Docker Compose as the fastest local workflow even after Kubernetes support lands

That means:

- do not replace the current architecture with a Kubernetes-only shortcut that removes Eureka or Config Server just because Kubernetes could handle part of that job differently later
- do not redesign service ownership or data flow while introducing manifests
- do not force a Helm migration or full GitOps workflow in this sprint
- do not let Kafka deployment complexity block the first Kubernetes cut of the application stack

## Scope Decisions For Sprint 9

To keep Sprint 9 credible and achievable, use the following defaults unless implementation reality forces a small adjustment:

### Cluster Target

Use `kind` as the default local cluster target.

Why:

- it works well with local Docker-built images
- it is lightweight enough for a monorepo demo workflow
- it provides a clear path for ingress and namespace-based validation

Document `minikube` only as a secondary option if a contributor cannot use `kind` locally.

### Image Workflow

Keep the current image model:

1. build Java JARs locally
2. build Docker images from the existing service Dockerfiles
3. load those images into `kind`
4. deploy manifests that reference those local images

Do not introduce a separate Kubernetes-only image build path unless there is a concrete blocker.

### Config Strategy

Keep `config-server` in native mode.

Preferred Sprint 9 approach:

1. convert `config-server/config-repo/` files into a ConfigMap or mounted files
2. mount that material into the `config-server` pod at a stable path
3. point `SPRING_CLOUD_CONFIG_SERVER_NATIVE_SEARCH_LOCATIONS` at that mounted path

This keeps centralized config semantics intact without inventing an external config repository.

### Service Discovery Strategy

Retain Eureka in-cluster even though Kubernetes DNS reduces part of its value.

Sprint 9 should document the tradeoff clearly:

- Kubernetes DNS can handle direct service resolution
- the repository architecture still includes Eureka and should continue to demonstrate it
- future cloud simplification is a later architecture decision, not a Sprint 9 implementation shortcut

### Ingress Strategy

Use a simple ingress story that is easy to reproduce locally.

Recommended starting point:

- install an ingress controller compatible with `kind`
- expose `api-gateway`, `grafana`, and `zipkin`
- prefer hostnames or local path routing that can be documented in a few commands

If ingress becomes unnecessarily heavy for one surface, allow temporary port-forward verification during development, but the sprint should still finish with a documented ingress path for the required surfaces.

### Data And Messaging Scope

For Sprint 9:

- run `postgres` and `redis` inside Kubernetes
- keep application manifests ready for the current service set
- defer Kafka-on-Kubernetes to Sprint 10

This means the first local Kubernetes cut may use a temporary hybrid approach for Kafka if needed during development, but the Sprint 9 plan should avoid centering the week on Kafka operator work.

### Monitoring Scope

Aim for baseline in-cluster observability, not a perfect production observability platform.

Required baseline:

- Prometheus can scrape at least the core Spring metrics endpoints
- Grafana still boots with provisioned datasource and dashboards
- Zipkin is reachable and receives spans from at least gateway plus one backend service

## Sprint 9 Deliverables

### 1. Kubernetes Base Structure

- create `k8s/` manifest structure for namespace, shared config, and app resources
- keep filenames and directories understandable enough for a new contributor to navigate quickly
- define a predictable apply order

### 2. Local Cluster And Image Workflow

- choose `kind` and document the choice
- document cluster creation and teardown commands
- document local image build and `kind load docker-image` flow
- make the workflow compatible with the repo's existing prebuilt-JAR Dockerfiles

### 3. Config Server Native Mode In Kubernetes

- convert in-repo config material into Kubernetes-mounted config
- mount it into `config-server`
- verify services can still resolve centralized config successfully

### 4. Core Platform Services In Kubernetes

- add Deployments and Services for the Spring apps, `ml-engine`, `postgres`, `redis`, `prometheus`, `grafana`, and `zipkin`
- add readiness and liveness probes where they meaningfully reduce startup races
- translate current environment variables and service URLs into Kubernetes-safe values

### 5. In-Cluster Discovery And Networking

- make service-to-service calls work with Kubernetes service names
- keep Eureka registration coherent in-cluster
- expose gateway and observability surfaces through ingress

### 6. Verification, Runbook, And Troubleshooting

- verify that pods become ready in the intended namespace
- verify `api-gateway` and at least one monitoring endpoint are reachable
- verify one real application path through the gateway in Kubernetes
- document the exact local runbook and common failure modes

## Suggested Manifest Layout

Keep Sprint 9 simple and explicit. A good starting layout is:

- `k8s/namespace.yaml`
- `k8s/config/`
- `k8s/platform/`
- `k8s/apps/`
- `k8s/monitoring/`
- `k8s/ingress/`

One practical interpretation is:

- `k8s/config/`
  - ConfigMaps and future Secret templates
- `k8s/platform/`
  - `eureka-server`, `config-server`, `postgres`, `redis`
- `k8s/apps/`
  - `api-gateway`, `chat-service`, `sentiment-service`, `video-service`, `recommendation-service`, `ml-engine`
- `k8s/monitoring/`
  - `prometheus`, `grafana`, `zipkin`
- `k8s/ingress/`
  - ingress resources for required entrypoints

The exact folder names can vary slightly, but the repo should end Sprint 9 with a structure that makes it obvious where to look for base config, platform services, application services, and monitoring resources.

## Required Scope Breakdown

## Phase 1 - Freeze The Kubernetes Runtime Strategy

1. Confirm `kind` as the default local target.
2. Freeze Sprint 9 scope to Kubernetes application deployment and runbook work, not Kafka operator work.
3. Confirm the local image workflow remains based on prebuilt JAR Docker images.
4. Freeze the rule that `config-server/config-repo/` stays in its current repository structure.
5. Decide the namespace name and manifest folder layout.
6. Decide the ingress approach for `api-gateway`, `grafana`, and `zipkin`.

### Expected end state

- Sprint 9 has a stable deployment strategy before manifest work begins
- the team avoids wasting time on Kubernetes alternatives that do not change the roadmap outcome

## Phase 2 - Create Namespace, Shared Config, And Apply Order

1. Add `k8s/namespace.yaml`.
2. Create base ConfigMaps for shared environment and Config Server native-mode files.
3. Create any placeholder Secret manifests or documented secret injection points needed later.
4. Define a clear apply order for namespace, config, platform services, app services, monitoring, and ingress.
5. Add dry-run validation for the initial manifest set.

### Expected end state

- the repo has a coherent Kubernetes base instead of ad hoc one-off manifests
- config material has a predictable home before service manifests start depending on it

## Phase 3 - Run `config-server` And `eureka-server` In Kubernetes

1. Add Service and Deployment manifests for `eureka-server`.
2. Add Service and Deployment manifests for `config-server`.
3. Mount the in-repo config files into `config-server` using Kubernetes-managed config.
4. Set the native search location to the mounted path.
5. Verify `config-server` health and one sample config fetch in-cluster.
6. Verify Spring services can still register through Eureka in Kubernetes.

### Expected end state

- the architectural backbone still works in Kubernetes
- centralized config and service discovery remain real parts of the runtime story rather than documentation fiction

## Phase 4 - Bring Up Core Stateful Dependencies

1. Add `postgres` Deployment, Service, storage configuration, and health checks suitable for local development.
2. Add `redis` Deployment, Service, and health checks.
3. Keep Sprint 9 storage simple and local-cluster-friendly rather than overengineering persistent storage classes.
4. Make service connection strings use Kubernetes DNS names.
5. Verify backend services can resolve and connect to `postgres` and `redis`.

### Expected end state

- local Kubernetes has the minimum stateful backing services needed for the platform's current read and write paths
- service configuration no longer depends on Docker Compose-only hostnames

## Phase 5 - Deploy Application Services And `ml-engine`

1. Add Service and Deployment manifests for:
   - `chat-service`
   - `sentiment-service`
   - `video-service`
   - `recommendation-service`
   - `api-gateway`
   - `ml-engine`
2. Translate current environment variables and upstream URLs into Kubernetes service names.
3. Add readiness and liveness probes using actuator or HTTP health endpoints where appropriate.
4. Keep startup dependencies practical rather than trying to exactly mimic Docker Compose `depends_on` semantics.
5. Verify pods reach ready state consistently enough for local smoke validation.

### Expected end state

- the application services run in-cluster with the same ownership boundaries they already have in Docker Compose
- the gateway can reach downstream services and expose the platform from inside Kubernetes

## Phase 6 - Add Monitoring Stack In Cluster

1. Add manifests for `prometheus`, `grafana`, and `zipkin`.
2. Translate existing provisioning mounts into ConfigMaps or mounted files.
3. Ensure Prometheus can scrape at least gateway and one backend service.
4. Ensure Grafana boots with a provisioned Prometheus datasource.
5. Ensure Zipkin is reachable and receives spans from at least a minimal request path.

### Expected end state

- the Kubernetes deployment has baseline metrics and trace visibility
- observability remains a first-class platform concern instead of a Docker-only feature

## Phase 7 - Expose Ingress And External Access

1. Add ingress resources for `api-gateway`, `grafana`, and `zipkin`.
2. Document any required local `/etc/hosts` entries or `kind` ingress setup steps.
3. Keep access paths simple enough to demo quickly.
4. Verify the required surfaces are reachable from the host machine.

### Expected end state

- a contributor can reach the deployed stack without manually port-forwarding every service
- the repo starts to look like a real platform deployment rather than only a collection of pods

## Phase 8 - Verify One Real End-To-End Platform Path

1. Validate `kubectl apply --dry-run=client -f k8s/`.
2. Apply the full manifest set to the local cluster.
3. Wait for pods and services to become ready in the target namespace.
4. Verify `api-gateway` health from outside the cluster.
5. Verify one monitoring surface such as Grafana or Prometheus is reachable.
6. Drive at least one real application path through the gateway in Kubernetes.
7. Confirm metrics and traces appear for that path.

### Suggested verification path

Use the smallest credible path already supported by the platform, such as:

- gateway health
- one GraphQL query
- one ingest path that reaches its owning service

If Kafka remains external or hybrid during the first cut, document that honestly and keep the verification centered on what Sprint 9 actually owns.

### Expected end state

- Sprint 9 proves the stack is not only declaratively deployed but also functionally reachable in Kubernetes
- the resulting demo is grounded in runtime behavior, not only successful manifest application

## Phase 9 - Finish Runbook And Troubleshooting Material

1. Add a local Kubernetes runbook to `docs/`.
2. Document prerequisites:
   - Docker
   - `kubectl`
   - `kind`
   - ingress setup requirements
3. Document exact commands for:
   - building JARs
   - building images
   - loading images into `kind`
   - creating the cluster
   - applying manifests
   - checking readiness
   - tearing the cluster down
4. Document common failure modes, especially:
   - image not present in cluster
   - Config Server native mount path mismatch
   - service DNS misconfiguration
   - readiness failures from startup ordering
5. Update `opencodeCommandHistory/` with the Sprint 9 implementation record.

### Expected end state

- a new contributor can reproduce the Kubernetes deployment without reverse-engineering shell history
- the repo's Kubernetes story becomes honest, testable, and teachable

## Testing Requirements

Sprint 9 testing should focus on deployment correctness and reachability rather than deep application regression coverage alone.

Required verification:

- `kubectl apply --dry-run=client -f k8s/`
- namespace creation and manifest apply succeed without manual YAML patching
- pods become ready for the targeted Sprint 9 stack
- `api-gateway` is reachable from the host
- one monitoring endpoint is reachable from the host
- one real application path works in-cluster

Useful additional validation if time allows:

- `kubectl logs` checks for Config Server startup correctness
- sample in-cluster curl against Config Server and Eureka
- manifest linting if a lightweight tool is already available locally

## Observability Requirements

Sprint 9 observability is complete only when:

- Prometheus can scrape at least gateway plus one backend service in-cluster
- Grafana boots with provisioned datasource and dashboard configuration
- Zipkin receives at least a minimal trace path from the running Kubernetes stack
- troubleshooting steps mention how to inspect pod health, logs, and service endpoints

## Demo Script

Sprint 9 should end with a short, believable Kubernetes demo:

1. create the `kind` cluster
2. build and load local images
3. apply the `k8s/` manifests
4. show pods becoming ready in the StreamSense namespace
5. open `api-gateway`, Grafana, and Zipkin through the documented local access path
6. drive one small application flow through the gateway
7. show resulting metrics or traces in-cluster

## Definition Of Done

Sprint 9 is complete when:

- `k8s/` contains a coherent first-pass Kubernetes deployment for the core platform
- the local cluster workflow is documented and reproducible on `kind`
- `config-server` native mode works in Kubernetes without moving the config repo out of its current structure
- gateway and baseline monitoring surfaces are reachable from the host machine
- at least one real platform flow is verified in Kubernetes
- the runbook and command history reflect the actual implementation rather than intended future work

## Risks To Watch

- underestimating the friction of Config Server native file mounting in Kubernetes
- accidentally coupling the sprint to Kafka-on-Kubernetes before the app layer is stable
- relying on Docker Compose startup assumptions that do not translate to Kubernetes readiness behavior
- introducing a second image build workflow that diverges from the repo's current Dockerfiles
- treating Eureka as irrelevant in-cluster and accidentally drifting away from the repository's intended architecture
- spending too much time on perfect persistence or ingress polish before basic application reachability is proven
