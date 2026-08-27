# hardening/02b-image-pinning

Priority 2 (part b) from `docs/planning/production-hardening.md`: every third-party container image is referenced by an exact tag plus its manifest digest, so a rebuild or a redeploy pulls the same bytes. Stacked on `hardening/01-secrets` because both touch `docker-compose.yml` and the Kubernetes manifests.

## What changed

28 files: `docker-compose.yml`, every manifest under `k8s/`, and all twelve `Dockerfile`s. No service code.

| Image | Before | After |
|---|---|---|
| busybox (34 init containers) | `1.36` | `1.36.1@sha256:73aaf090…` |
| confluentinc/cp-kafka | `7.6.1` | `7.6.1@sha256:620734d9…` |
| confluentinc/cp-zookeeper | `7.6.1` | `7.6.1@sha256:4dc78064…` (removed entirely in branch 05) |
| danielqsj/kafka-exporter | `latest` | `v1.9.0@sha256:4150e46b…` |
| eclipse-temurin (8 Java Dockerfiles) | `21-jre` | `21.0.12_8-jre@sha256:7a65df4b…` |
| grafana/grafana | `latest` | `13.2.1@sha256:f772d434…` |
| minio/minio | `RELEASE.2025-04-22T22-12-26Z` | same tag `@sha256:a1ea29fa…` |
| nginx (frontend) | `alpine` | `1.31.5-alpine@sha256:34f40471…` |
| node (frontend build stage) | `20-alpine` | `20.20.2-alpine@sha256:fb4cd12c…` |
| openzipkin/zipkin | `latest` | `3.6.1@sha256:d17e856d…` |
| postgres | `16` | `16.15@sha256:f1c3376c…` |
| prom/prometheus | `latest` | `v3.14.0@sha256:5ce7540c…` |
| provectuslabs/kafka-ui | `latest` | `v0.7.2@sha256:8f2ff02d…` |
| python (2 Dockerfiles) | `3.11-slim` | `3.11.16-slim@sha256:9534e5a8…` |
| redis | `7-alpine` | `7.4.11-alpine@sha256:ff02b58f…` |

Selection rule: the newest patch release inside the major (or minor, for Postgres and Python) that the floating tag already resolved to, so nothing changes version intent. The five `:latest` images were already floating to their newest release, so they are pinned to that release. Digests are the multi-architecture manifest-list digests from `docker buildx imagetools inspect`, which work on both amd64 and arm64 hosts.

The `tag@sha256:` form is the one Renovate's `docker:pinDigests` preset maintains, so branch 14 can keep these current without hand edits.

## Deliberately left alone

- `streamsense/*:sprint9` application images. Those are built locally or by the smoke job and never published, so there is no digest to pin. Versioned app tags arrive with the image-publishing workflow in branch 14.
- `provectuslabs/kafka-ui` v0.7.2 is the final release under that name; the project continues as `kafbat/kafka-ui`. Switching is a product change and belongs in the KRaft branch, which touches the Kafka UI configuration anyway.
- The `busybox:1.36` in a manual debugging command in `docs/kubernetes-kind.md` and the archived sprint notes are prose, not manifests.

## Verification

| Check | Command | Result |
|---|---|---|
| No floating third-party refs | script assertion over every `image:` and `FROM` in Compose, `k8s/**`, and `*/Dockerfile` | none remain |
| Compose renders | `docker compose config -q` | OK |
| kustomize renders | `kubectl kustomize k8s` | OK |
| Every pin resolves | `docker buildx imagetools inspect <ref>` for all 15 unique `tag@digest` refs | all 15 resolve |
| Dockerfiles build on pinned bases | `docker build video-capture-service` and `docker build frontend` | both build successfully (video-capture-service 79febb6d…, frontend built through the node and nginx stages) |

The Java Dockerfiles copy a prebuilt jar, so they were not built here; the base image reference is the only change and it resolves (row 4). ml-engine's build installs CPU torch and was not rebuilt locally; CI's smoke job builds it.

## Manual checks for the reviewer

1. `make up` (or `tools/start-stack.ps1 -TwitchEnv -Channels redbull-testing`) on the branch pulls the pinned images and every service reaches healthy.
2. Grafana at `http://localhost:3001` (now 13.2.1) still provisions the four dashboards and the Prometheus datasource; Prometheus v3 scrapes all targets (`http://localhost:9090/targets`).
3. Kafka UI at `http://localhost:8088` still shows the cluster and topics.
4. `make replay-smoke` passes.
5. On kind, `kubectl apply -k k8s` pulls the digest-pinned infra images (`kind load` is still needed for the `streamsense/*` app images).

## Follow-ups (not in this branch)

- Branch 14 adds Renovate (`config:best-practices`, which includes `docker:pinDigests`) so these digests are bumped by bot PRs.
- Branch 05 removes the ZooKeeper image and moves Kafka to KRaft.
- Branch 08 replaces the single-stage Java Dockerfiles with the layered, non-root template; the pinned base carries over.
