# capture/02-drop-recommendations

recommendation-service leaves the stack. Planned in `docs/planning/redesign/product-direction.md` ("the recommendations panel: no story asks for it; the service is removed from the stack in a later branch") and left open by `redesign/01-ops-route`, which deleted the console panel and its query but kept the service running.

## Why now

Nothing has queried it since the redesign shipped: the console has no recommendations panel, the GraphQL field was answered only by the gateway's own resolver, and no service consumed its output. It still cost a Maven module, a container in Compose and in the production overlay, a Deployment and a NetworkPolicy in Kubernetes, a Prometheus target, a CI matrix entry and a published image, an init container the gateway waited on before it would start, and a step in the smoke test. Eleven service images become ten; the gateway starts one health-check wait sooner.

## What changed

- **Deleted:** the `recommendation-service` module (26 files) and `k8s/apps/recommendation-service.yaml`, plus the gateway's `client/RecommendationServiceClient`, `graphql/RecommendationGraphqlController`, `model/Recommendation`, `graphql/recommendation.graphqls`, the `recommendations` field on the `Query` type, `RecommendationsQueryTest`, and `config-server/config-repo/recommendation-service.yml`.
- **Unwired:** the module from the root `pom.xml` and the makefile's `JAVA_SERVICES`; the service and the gateway's `depends_on` entry in `docker-compose.yml`; the overlay entry in `docker-compose.prod.yml`; the base URL and the `/api/recommendations/**` route in `config-server/config-repo/api-gateway.yml`; the `recommendationService` property in `DownstreamServicesProperties` and the URL in the gateway's test config; the Prometheus target in both `monitoring/prometheus/prometheus.yml` and `k8s/config/prometheus-config.yaml`; the resource in `k8s/kustomization.yaml` and the config file in the root `kustomization.yaml`; the gateway's `wait-for-recommendation-service` init container; the path filter, the Java matrix entry, the smoke build and start lists, the "Verify GraphQL recommendations query" step, and the two image lists in `.github/workflows/ci.yml`; the query and assertion in `tools/smoke/compose_smoke.py` and `tools/demo/seed_demo.py`.
- **Network policies:** the `recommendation-service` policy document and its seven peer entries in other policies. Removing a peer left two egress rules with a port and no peer, which NetworkPolicy reads as "any destination on port 8082" — wider than before, not narrower — so those rules went too. `tools/k8s/check_network_policies.py` derives 60 edges and all are allowed.
- **Tests kept, retargeted:** `GraphQlErrorAdviceTest` used the recommendations query as its vehicle for the downstream error mapping (503, a problem body, a 404, an undecodable body); it now points a MockWebServer at sentiment-service and asks `recentSentiment`, so the four `extensions.code` cases still run. `DownstreamServicesPropertiesTest` drops the URL from its full set and omits chat-service's instead of recommendation-service's in the "a missing URL fails startup" case. `DownstreamWebClientTimeoutTest` only needed a path to call; it uses an analytics one. `GraphqlSchemaContractTest` no longer expects the field.
- **Docs:** the service map, data flow, module count (eight → seven), Java matrix count, and published image count (eleven → ten) in `CLAUDE.md`; the mermaid nodes and edges in `README.md` and `docs/architecture.md`; the `docs/howtorun.md` "Sprint 8 quickstart" section (88 lines of recommendation verification); the two build lines in `docs/kubernetes-kind.md`; the check lists in `tools/smoke/README.md` and `tools/demo/README.md`; the status lines in `docs/planning/redesign/README.md`. The branch notes under `docs/planning/branches/` and `history/` keep their record of the service as it was.

## Verification

| Check | Result |
|---|---|
| `mvn verify` api-gateway | 122 tests, 5 skipped (Redis Testcontainer), Spotless, ArchUnit, JaCoCo floor green |
| `mvn -DskipTests package` (reactor) | seven modules build |
| frontend `codegen:check`, `eslint`, `vitest run --coverage` | clean; 138 tests, floors held (the regenerated `generated.ts` drops the `Recommendation` types) |
| `docker compose config -q`, base and with the production overlay | both render |
| `tools/k8s/check_network_policies.py` | 60 edges, all allowed by 19 policies |
| `python3 -m ast` on the two changed tools, YAML parse of the workflow and both config files | clean |

## Review round (Codex on #68)

One P1: the service was gone but its **health probe was not**. The `docker-smoke` job's "Wait for core
services" loop and `ROOT_HEALTH_URLS` in `tools/smoke/compose_smoke.py` both still curled
`http://localhost:8082/actuator/health`, which nothing serves now, so every smoke run and every
`make smoke-e2e` would have waited out its timeout and failed. Both probes are gone. Removing a service
means removing what waits on it, not only what names it: the sweep for "recommendation" missed a bare port.

## Deliberately left alone

- **The history.** `docs/planning/history/` and the earlier branch notes still describe the service; they are records of what was done, not documentation of what runs.
- **`frontend/README.md`** keeps its stale row for `src/features/evidence/` and `metrics/`, directories the redesign already removed; only the word "recommendation" left it. Refreshing that file belongs to whoever next touches it.
- **No replacement.** Nothing in the seven user stories asks for recommendations; if one ever does, it comes back as a feature with a story, not as a resurrected service.
