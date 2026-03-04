# Local Runbook

This runbook describes how to run StreamSense-Production locally (Docker Compose first), and how to debug common issues.

## Prereqs
- Java 21
- Maven
- Docker + Docker Compose
- Node (for frontend later)

## Quickstart: Docker Compose (recommended)

### Build jars (required: Dockerfiles copy target/*.jar)
From repo root:

- Build all services:
  - eureka-server
  - config-server
  - api-gateway
  - chat-service
  - sentiment-service
  - video-service
  - recommendation-service

(Use either a Makefile target or run `mvn -DskipTests package` in each service directory.)

### Start the stack
From repo root:
- `docker compose up -d --build`

### Verify
- Eureka UI: http://localhost:8761
- Config server: http://localhost:8888/<service>/default
- Health checks (ports depend on config-repo):
  - api-gateway: http://localhost:8080/actuator/health
  - chat-service: http://localhost:8081/actuator/health
  - sentiment-service: http://localhost:8082/actuator/health
  - recommendation-service: http://localhost:8083/actuator/health
  - video-service: http://localhost:8084/actuator/health

## Local run: Maven (fast iteration)

### Start order (local JVM)
1) eureka-server
2) config-server
3) api-gateway
4) other services

Example:
- `cd eureka-server && mvn spring-boot:run`
- `cd config-server && mvn spring-boot:run`
- `cd api-gateway && mvn spring-boot:run`

## Common failure modes

### 1) Service registers late / not immediately visible in Eureka
Symptoms:
- service health endpoint works
- Eureka UI doesn’t show the service immediately
- logs show temporary registration errors

Cause:
- Eureka not fully ready when the client starts
- Compose `depends_on` does not guarantee readiness (unless healthcheck gating is used)

Fix:
- Wait 30–60 seconds (Eureka client retries automatically)
- Prefer healthcheck-gated depends_on for deterministic startup
- Check logs: `docker compose logs <service>`

### 2) “Cannot execute request on any known server” (Eureka client)
Symptoms:
- logs in a service show:
  - `Cannot execute request on any known server`

Cause:
- service is trying to reach Eureka at a bad URL or Eureka is not ready

Fix:
- Ensure config-repo uses Docker hostnames:
  - `http://eureka-server:8761/eureka` (inside containers)
- Verify served config:
  - `curl http://localhost:8888/<service>/default`
- Verify connectivity from container:
  - `docker compose exec <service> sh -lc "wget -qO- http://eureka-server:8761 | head"`

### 3) Config Server works locally, fails in Docker
Symptoms:
- config-server returns 404/empty or doesn’t see configs in Docker

Cause:
- using absolute host filesystem path in `search-locations` (works locally, not in container)

Fix:
- mount repo configs: `./config-server/config-repo:/config-repo`
- set env var in compose:
  - `SPRING_CLOUD_CONFIG_SERVER_NATIVE_SEARCH_LOCATIONS=file:/config-repo`

### 4) Service fails to fetch config in Docker
Symptoms:
- service starts with defaults or fails early
- config import points to localhost

Cause:
- inside container, `localhost` refers to the container itself

Fix:
- set service bootstrap config import to:
  - `optional:configserver:http://config-server:8888`

### 5) Random host ports (e.g. 0.0.0.0:57062->8084)
Cause:
- compose ports configured as `"8084"` rather than `"8084:8084"`

Fix:
- always map explicitly:
  - `"HOST:CONTAINER"` e.g. `"8084:8084"`

## Useful commands

- See running containers:
  - `docker compose ps`

- Tail logs:
  - `docker compose logs -f <service>`

- Restart one service:
  - `docker compose restart <service>`

- Rebuild one service:
  - `docker compose up -d --build <service>`