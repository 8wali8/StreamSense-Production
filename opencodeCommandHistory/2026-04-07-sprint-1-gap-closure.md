# Week 1 Work Log: Platform Gap Closure

## Objective

Close the remaining Week 1 platform gaps from `plan.md` without overbuilding.

Constraints followed:

- keep `config-server/config-repo/` instead of moving to a root `config-repo/`
- keep the current prebuilt-JAR Docker workflow
- stay Docker-first for runtime behavior
- use the current Maven and pytest setup rather than introducing a new frontend test framework

## Scope Completed

### 1. Repo standards and metadata cleanup

Files changed:

- `.editorconfig`
- `.gitignore`
- `makefile`
- `video-service/pom.xml`

Changes made:

- added a root `.editorconfig`
- expanded `.gitignore` for Java, Python, frontend, IDE, and OS artifacts
- updated the root `makefile` so the listed services match the actual repo
- updated the `test` workflow in `makefile` to cover Java services, `ml-engine`, and frontend checks
- fixed `video-service` Maven metadata so `artifactId` and `name` match the actual service

Reason:

- Week 1 required repo standards and consistent service naming across metadata and tooling

### 2. Config Server path normalization

Files changed:

- `config-server/src/main/resources/application.yml`
- `config-server/config-repo/application.yml`
- `config-server/config-repo/api-gateway.yml`
- `config-server/config-repo/chat-service.yml`
- `config-server/config-repo/sentiment-service.yml`
- `config-server/config-repo/video-service.yml`
- `config-server/config-repo/recommendation-service.yml`

Changes made:

- removed the machine-specific absolute path from Config Server native search locations
- kept the existing `config-server/config-repo/` layout
- added shared config defaults in `config-server/config-repo/application.yml`
- moved repeated observability defaults into shared config
- changed Eureka default zones in service config files to use `${EUREKA_DEFAULT_ZONE:...}`

Reason:

- Week 1 required stable config lookup behavior without hardcoding one machine path
- shared defaults were repeated across service config files and were better centralized

### 3. Docker-first endpoint configurability

Files changed:

- `api-gateway/src/main/resources/application.yml`
- `chat-service/src/main/resources/application.yml`
- `sentiment-service/src/main/resources/application.yml`
- `video-service/src/main/resources/application.yml`
- `recommendation-service/src/main/resources/application.yml`

Changes made:

- changed each service to import Config Server through `${CONFIG_SERVER_URL:http://config-server:8888}`

Reason:

- this keeps Docker as the default runtime path while still allowing controlled overrides when needed

### 4. Shared log pattern and HTTP correlation filter baseline

Files changed:

- `config-server/src/main/resources/application.yml`
- `eureka-server/src/main/resources/application.yml`
- `config-server/config-repo/application.yml`
- `api-gateway/src/main/java/com/streamsense/apigateway/config/CorrelationIdWebFilter.java`
- `chat-service/src/main/java/com/streamsense/chatservice/config/CorrelationIdFilter.java`
- `sentiment-service/src/main/java/com/streamsense/sentimentservice/config/CorrelationIdFilter.java`
- `video-service/src/main/java/com/streamsense/videoservice/config/CorrelationIdFilter.java`
- `recommendation-service/src/main/java/com/streamsense/recommendationservice/config/CorrelationIdFilter.java`
- `config-server/src/main/java/com/streamsense/configserver/config/CorrelationIdFilter.java`
- `eureka-server/src/main/java/com/streamsense/eureka/config/CorrelationIdFilter.java`
- `chat-service/src/main/java/com/streamsense/chatservice/controller/ChatIngestController.java`
- `chat-service/src/test/java/com/streamsense/chatservice/controller/ChatIngestControllerTest.java`

Changes made:

- added a shared logging pattern including `traceId`, `spanId`, and `correlationId`
- added HTTP correlation ID filters to the Spring services
- added a reactive correlation ID filter for `api-gateway`
- standardized on `X-Correlation-Id` while still accepting the legacy `correlationId` header
- made `chat-service` publish using the resolved correlation ID
- added a test assertion that the ingest response includes `X-Correlation-Id`

Reason:

- Week 1 required a shared observability baseline and correlation ID propagation for HTTP requests

### 5. Compose healthchecks and dependency ordering

File changed:

- `docker-compose.yml`

Changes made:

- removed the obsolete Compose `version` key
- added a healthcheck for `config-server`
- changed Spring service dependencies on `config-server` to `condition: service_healthy`
- changed `config-server` dependency on `eureka-server` to `condition: service_healthy`

Reason:

- Week 1 required healthchecks where they prevent startup races

### 6. Missing Spring Boot smoke tests

Files added:

- `eureka-server/src/test/java/com/streamsense/eureka/EurekaServerApplicationTests.java`
- `config-server/src/test/java/com/streamsense/configserver/ConfigServerApplicationTests.java`
- `api-gateway/src/test/java/com/streamsense/apigateway/ApiGatewayApplicationTests.java`
- `chat-service/src/test/java/com/streamsense/chatservice/ChatServiceApplicationTests.java`
- `sentiment-service/src/test/java/com/streamsense/sentimentservice/SentimentServiceApplicationTests.java`
- `video-service/src/test/java/com/streamsense/videoservice/VideoServiceApplicationTests.java`
- `recommendation-service/src/test/java/com/streamsense/recommendationservice/RecommendationServiceApplicationTests.java`

Files adjusted for test support:

- `config-server/src/test/java/com/streamsense/configserver/ConfigServerApplicationTests.java`
- `eureka-server/src/test/resources/application.yml`

Changes made:

- added `contextLoads` coverage across all Spring services
- used the existing Maven test setup
- disabled or adjusted only the minimum external behavior needed for tests to boot cleanly

Reason:

- Week 1 required boot smoke coverage per service

### 7. CI coverage updates

File changed:

- `.github/workflows/ci.yml`

Changes made:

- renamed the Java job to `java-build-test`
- kept Maven test coverage for all Spring services
- added Python lint with `ruff` before pytest in `ml-engine`
- added frontend `npm ci`, `npm run lint`, and `npm run build`
- added `docker compose config`
- added a Docker smoke job for the Week 1 baseline stack using the current prebuilt-JAR flow

Reason:

- Week 1 required CI coverage for Java, Python, frontend, and Compose validation

### 8. Documentation fixes

Files changed:

- `README.md`
- `docs/howtorun.md`

Changes made:

- documented the Docker-first workflow clearly
- documented that Java JARs must be built before `docker compose up --build`
- corrected the Kafka UI port to `8088`
- added Grafana URL and Config Server verification info
- corrected the `docs/` reference in the README
- documented optional host-local overrides while keeping Docker as the default path

Reason:

- Week 1 required docs and runbook accuracy for the working platform shape

## Verification Performed

### Java tests

Commands run:

```bash
mvn -q test
```

Run successfully in:

- `eureka-server/`
- `config-server/`
- `api-gateway/`
- `chat-service/`
- `sentiment-service/`
- `video-service/`
- `recommendation-service/`

### Frontend checks

Command run in `frontend/`:

```bash
npm ci && npm run lint && npm run build
```

Result:

- passed locally

### Python tests

Command run in `ml-engine/`:

```bash
PYTHONPATH=src/main/python python3 -m pytest src/test/python
```

Result:

- 4 tests passed

### Compose config validation

Command run at repo root:

```bash
docker compose config
```

Result:

- Compose configuration validated successfully

### Docker runtime validation attempt

Command attempted at repo root:

```bash
mvn -q -f eureka-server/pom.xml -DskipTests package && mvn -q -f config-server/pom.xml -DskipTests package && docker compose up -d --build eureka-server config-server zipkin prometheus grafana
```

Result:

- blocked by the local environment because Docker daemon access was unavailable
- error returned:
  - `Cannot connect to the Docker daemon at unix:///Users/ujjawalprasad/.docker/run/docker.sock`

## Important Notes

- no Kafka port change was made
- the current Docker networking assumptions were preserved
- the current prebuilt-JAR Dockerfile workflow was preserved and documented better
- no new frontend test framework was added
- existing unrelated user changes in `plan.md` and `weeklyplans/` were not modified

## Net Effect

After this work:

- Week 1 repo standards are in better shape
- Config Server pathing is stable without a machine-specific absolute path
- Spring services have a shared logging and correlation ID baseline
- Compose startup ordering is stricter around healthy core services
- all Spring services now have boot smoke coverage through Maven
- CI now reflects the intended Week 1 coverage better
- docs match the current Docker-first, prebuilt-JAR workflow
