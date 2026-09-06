# AGENTS.md

## Start Here

- Read `docs/current-state.md` before doing non-trivial work. It captures the current replay milestone, known gaps, and the recommended next task.
- Read `docs/replay-runbook.md` before starting or debugging the Twitch VOD replay stack.
- Read `docs/next-work.md` when choosing what to build next.
- Current canonical demo channel is `redbull-testing`, backed by Twitch VOD `2750461300` and source marker `TWITCH_VOD_REPLAY`.

## Workflow

- Root task runner is `makefile` (lowercase). Use `make help` for repo commands.
- This is not a Maven monorepo. Each Java service has its own `pom.xml`; run Maven in the service directory or with `mvn -f <service>/pom.xml ...`.
- CI uses Java 21, Python 3.11, and Node 20.

## Build And Verify

- Java service check: `cd <service> && mvn -B -ntp clean test`
- Focused Java test: `cd <service> && mvn -Dtest=MyTest test`
- ML checks: `cd ml-engine && pip install -r requirements.txt ruff==0.16.3 && ruff check src/main/python src/test/python && PYTHONPATH=src/main/python pytest src/test/python`
- Frontend CI check: `cd frontend && npm ci && npm run lint && npm run test && npm run build`
- `make test` is not identical to CI: it runs Java tests and ML tests, but for `frontend` it only runs `npm run lint && npm run build` and skips Vitest.
- If you touch `k8s/` or `config-server/config-repo/`, run `kubectl kustomize .` from the repo root. CI also validates the JSON embedded in `k8s/config/grafana-config.yaml`.

## Config And Wiring

- The Spring services' `src/main/resources/application.yml` files are bootstrap-only. Real runtime config lives in `config-server/config-repo/*.yml`.
- Kubernetes generates the config-server ConfigMap from `config-server/config-repo/*.yml` (root `kustomization.yaml`); there is no mirrored copy. Apply and render from the repo root: `kubectl kustomize .`, `kubectl apply -k .`.
- Every Kubernetes container has `resources` and a non-root `securityContext`; Spring probes use `/actuator/health/liveness` and `/readiness`; stateful volumes are PVCs.
- Never write a credential into a committed file. Compose reads `secrets/<NAME>` (git-ignored, created with random values by `make secrets`), Spring resolves `${NAME}` placeholders through `configtree:/run/secrets/`, Python services accept `<NAME>_FILE`, and Kubernetes builds `streamsense-secrets` from `k8s/secrets/streamsense.env`. See `secrets/README.md`.
- Downstream service URLs have no defaults; a missing `streamsense.services.<name>.base-url` fails startup. Every HTTP client (WebClient, RestClient, RestTemplate) carries connect and read/response timeouts from properties; never construct one without them.
- The config-server import is required and retried. Tests stay self-contained because every Java service ships `src/test/resources/application.yml` with config-server and Eureka disabled; a service without that file cannot run its tests.
- Useful env toggles:
  - `CONFIG_SERVER_URL`
  - `STREAMSENSE_GATEWAY_AUTH_ENABLED`
  - `ML_ENGINE_FORCE_FAILURE`

## Docker And Local Run

- Java Dockerfiles copy `target/*jar`. After Java changes, build jars first with `make package` or `cd <service> && mvn -DskipTests package`, then rebuild images.
- `make up` depends on `make package`, then builds images and starts Compose. `make build` only builds Docker images and does not package Java jars.
- `tools/start-stack.ps1` packages Java services inside a Maven Docker container, then builds/starts Compose and can switch runtime Twitch channels. Prefer it for the current replay demo on Windows.
- Full replay startup: `powershell -ExecutionPolicy Bypass -File "tools/start-stack.ps1" -TwitchEnv -Channels redbull-testing`.
- Faster replay startup when jars are current: `powershell -ExecutionPolicy Bypass -File "tools/start-stack.ps1" -SkipPackage -TwitchEnv -Channels redbull-testing`.
- Full Docker Compose serves the frontend at `http://localhost:3000`; nginx proxies `/graphql` to `api-gateway`.
- Local frontend dev is different: `npm run dev` uses Vite defaults and there is no Vite proxy for `/graphql`. For end-to-end browser checks, prefer the Docker frontend unless you add your own proxy.

## Replay Context

- Replay alias config for Spring services lives in `config-server/config-repo/chat-service.yml`.
- Kubernetes reads the same file through the generated config-server ConfigMap (root `kustomization.yaml`).
- `video-capture-service` does not consume Config Server, so replay aliases are mirrored as environment variables in `docker-compose.yml` and `k8s/apps/video-capture-service.yaml`.
- Replay chat fixture: `chat-service/src/main/resources/replay/redbull-testing-chat.json`.
- Replay events should continue through normal Kafka/service paths keyed by `streamer="redbull-testing"`; avoid adding replay-only downstream branches unless there is a concrete need.
- Keep raw transcript/chat feeds independent from sponsor-filtered sentiment. Sponsor sentiment can be empty while raw transcript/chat is healthy.

## Git Hygiene

- Do not commit `.env.twitch.local`, OAuth tokens, captured media/frame artifacts, generated Java `target/**` output, or local `session-ses_*.md` notes.
- Generated `target/**` output is ignored and no longer tracked; if `git status` ever shows build artifacts, fix `.gitignore` rather than committing them.

## Test Behavior

- Java tests are mostly self-contained: test configs disable Config Server and Eureka; integration tests use Embedded Kafka, H2, and MockWebServer instead of requiring the Docker stack.
- `sentiment-service` and `video-service` use Flyway migrations under `src/main/resources/db/migration/`. `video-service` also uses a custom Flyway history table and baseline settings from config.

## Entry Points

- GraphQL gateway and subscriptions: `api-gateway/src/main/java/com/streamsense/apigateway/`
- ML FastAPI app: `ml-engine/src/main/python/app/main.py`
- Frontend Apollo transport setup: `frontend/src/apollo/client.ts`
