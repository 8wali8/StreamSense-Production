# Sprint 2 Work Log: Real-Time Slice Gap Closure

## Objective

Finish the remaining Sprint 2 gaps from `weeklyplans/week-2.md` while preserving the already-working Docker-first runtime path.

Constraints followed:

- keep the existing Kafka listener and port setup because it already works
- keep the current Docker-first flow
- leave the existing Sprint 3 overlap in `chat-service` alone
- make only the minimum changes needed to close the remaining Sprint 2 gaps

## Scope Completed

### 1. Kafka startup and topic-init hardening

File changed:

- `docker-compose.yml`

Changes made:

- added a Kafka healthcheck
- made `kafka-topics-init` depend on healthy Kafka
- made `chat-service` depend on healthy Kafka and successful topic initialization
- made `api-gateway` depend on healthy Kafka and successful topic initialization

Reason:

- Sprint 2 required reliable startup of Kafka and stable topic creation for `stream.chat.messages`
- the stack already worked, but startup ordering still relied too much on timing and retries

### 2. Frontend test coverage for the live chat surface

Files changed:

- `frontend/package.json`
- `frontend/package-lock.json`
- `frontend/vite.config.ts`
- `frontend/src/test/setup.ts`
- `frontend/src/components/Health.test.tsx`
- `frontend/src/pages/LiveChat.test.tsx`

Changes made:

- added a minimal test setup using `vitest`, `jsdom`, and Testing Library
- added a `test` script to the frontend package
- added a Vitest config block to Vite config
- added a `Health` test proving the success state renders
- added `LiveChat` tests proving:
  - initial disconnected state renders
  - listening empty state renders after connect
  - an incoming subscription event renders correctly

Reason:

- Sprint 2 explicitly required frontend test coverage for the thin slice
- the repo already had the live chat UI working manually, but not yet automated

### 3. CI upgrade from infrastructure smoke to Sprint 2 slice smoke

File changed:

- `.github/workflows/ci.yml`

Changes made:

- added `npm run test` to the frontend CI job
- expanded the Docker smoke job to package `api-gateway` and `chat-service` JARs in addition to the existing baseline services
- changed the smoke stack to start:
  - `zookeeper`
  - `kafka`
  - `kafka-topics-init`
  - `eureka-server`
  - `config-server`
  - `ml-engine`
  - `chat-service`
  - `api-gateway`
  - `frontend`
- added smoke checks for:
  - Kafka topic existence
  - successful chat ingest response
  - GraphQL `health` returning `ok`
  - frontend serving HTML

Reason:

- Sprint 2 required automated evidence for the thin real-time slice, not only Week 1 infrastructure checks
- `ml-engine` was included because the current `chat-service` consumer already depends on it

### 4. Sprint 2 runbook and verification cleanup

File changed:

- `docs/howtorun.md`

Changes made:

- added an explicit Sprint 2 quickstart section
- added the command to verify `stream.chat.messages` exists
- added the frontend URL to the infrastructure table
- documented frontend verification at `http://localhost:3000`
- added a short Sprint 2 verification checklist covering:
  - topic creation
  - ingest success
  - GraphQL health
  - subscription delivery
  - frontend live updates
  - metrics visibility

Reason:

- Sprint 2 required docs that allow another contributor to run and verify the thin slice without guessing

## Verification Performed

### Frontend checks

Command run in `frontend/`:

```bash
npm run lint && npm run test && npm run build
```

Result:

- passed locally
- frontend tests passed:
  - 4 tests total
  - `Health` success rendering
  - `LiveChat` disconnected, listening, and incoming-event rendering

### Compose validation

Command run at repo root:

```bash
docker compose config
```

Result:

- Compose configuration validated successfully
- `service_completed_successfully` dependency wiring resolved correctly

### Java backend checks

Commands run:

```bash
cd chat-service && mvn -q test
cd api-gateway && mvn -q test
```

Result:

- both test suites passed locally
- existing backend Sprint 2 tests still worked after the Compose/CI/frontend changes

### Full Docker Sprint 2 smoke validation

Commands run:

```bash
cd eureka-server && mvn -q -DskipTests package
cd config-server && mvn -q -DskipTests package
cd api-gateway && mvn -q -DskipTests package
cd chat-service && mvn -q -DskipTests package
```

Then from repo root:

```bash
docker compose up -d --build zookeeper kafka kafka-topics-init eureka-server config-server ml-engine chat-service api-gateway frontend
```

Verification performed:

```bash
docker compose exec -T kafka kafka-topics --bootstrap-server kafka:9092 --list
curl -fsS -X POST http://localhost:8081/api/chat/ingest -H 'Content-Type: application/json' -d '{"streamer":"smoke-test","user":"u1","message":"hello from smoke test","timestamp":1710000000000}'
curl -fsS http://localhost:8080/graphql -H 'Content-Type: application/json' -d '{"query":"query { health }"}'
curl -fsS http://localhost:3000
```

Result:

- Kafka topic `stream.chat.messages` existed
- chat ingest returned an `eventId`
- GraphQL `health` returned `ok`
- frontend served HTML successfully on port `3000`

## Important Notes

- no Kafka port changes were made
- no GraphQL protocol changes were made
- the current Sprint 3 overlap in `chat-service` was intentionally left in place
- the Docker stack was left running after smoke validation

## Net Effect

After this work:

- Sprint 2 startup is more reliable around Kafka and topic creation
- the frontend real-time slice now has automated test coverage
- CI now proves more of the Sprint 2 live chat slice instead of only infrastructure setup
- the runbook now explains how to verify the Sprint 2 slice end to end
