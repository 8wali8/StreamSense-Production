# AGENTS.md

## Workflow

- Root task runner is `makefile` (lowercase). Use `make help` for repo commands.
- This is not a Maven monorepo. Each Java service has its own `pom.xml`; run Maven in the service directory or with `mvn -f <service>/pom.xml ...`.
- CI uses Java 21, Python 3.11, and Node 20.

## Build And Verify

- Java service check: `cd <service> && mvn -B -ntp clean test`
- Focused Java test: `cd <service> && mvn -Dtest=MyTest test`
- ML checks: `cd ml-engine && pip install -r requirements.txt ruff && ruff check src/main/python src/test/python && PYTHONPATH=src/main/python pytest src/test/python`
- Frontend CI check: `cd frontend && npm ci && npm run lint && npm run test && npm run build`
- `make test` is not identical to CI: it runs Java tests and ML tests, but for `frontend` it only runs `npm run lint && npm run build` and skips Vitest.
- If you touch `k8s/`, run `kubectl kustomize k8s`. CI also validates the JSON embedded in `k8s/config/grafana-config.yaml`.

## Config And Wiring

- The Spring services' `src/main/resources/application.yml` files are bootstrap-only. Real runtime config lives in `config-server/config-repo/*.yml`.
- If you change config that Kubernetes uses, mirror the same change in `k8s/config/config-server-config-repo.yaml`; it duplicates the config-server repo as a ConfigMap.
- Useful env toggles:
  - `CONFIG_SERVER_URL`
  - `STREAMSENSE_GATEWAY_AUTH_ENABLED`
  - `ML_ENGINE_FORCE_FAILURE`

## Docker And Local Run

- Java Dockerfiles copy `target/*jar`. After Java changes, build jars first with `make package` or `cd <service> && mvn -DskipTests package`, then rebuild images.
- `make build` and `make up` do not package Java jars for you.
- Full Docker Compose serves the frontend at `http://localhost:3000`; nginx proxies `/graphql` to `api-gateway`.
- Local frontend dev is different: `npm run dev` uses Vite defaults and there is no Vite proxy for `/graphql`. For end-to-end browser checks, prefer the Docker frontend unless you add your own proxy.

## Test Behavior

- Java tests are mostly self-contained: test configs disable Config Server and Eureka; integration tests use Embedded Kafka, H2, and MockWebServer instead of requiring the Docker stack.
- `sentiment-service` and `video-service` use Flyway migrations under `src/main/resources/db/migration/`. `video-service` also uses a custom Flyway history table and baseline settings from config.

## Entry Points

- GraphQL gateway and subscriptions: `api-gateway/src/main/java/com/streamsense/apigateway/`
- ML FastAPI app: `ml-engine/src/main/python/app/main.py`
- Frontend Apollo transport setup: `frontend/src/apollo/client.ts`
