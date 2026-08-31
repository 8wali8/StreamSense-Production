# hardening/08-boot-upgrade

Priority 7 (part 2) from `docs/planning/production-hardening.md`: move the Java services from Spring Boot 3.2.12 (out of open-source support) to the current 3.5 line, and take the platform features that come with it. Stacked on `hardening/07-parent-pom`, which is what makes this a three-line version change instead of eight.

## What changed

**Versions (parent POM only)**: Spring Boot 3.2.12 → 3.5.16, Spring Cloud 2023.0.4 → 2025.0.3, Resilience4j 2.2.0 → 2.4.0. Nimbus stays at 9.37.3. Everything else moves through the BOMs (Spring Framework 6.2, Spring Kafka 3.3, Spring Data 2025.0, Flyway 11, Hibernate 6.6, Jackson 2.19, Micrometer 1.15).

**Two API migrations the upgrade forces:**

- `RestTemplateBuilder.setConnectTimeout/setReadTimeout` were deprecated in 3.4; sentiment-service and video-service now call `connectTimeout(Duration)` / `readTimeout(Duration)`.
- `org.springframework.boot.web.client.ClientHttpRequestFactories` was deprecated in 3.4 in favour of `org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder`; recommendation-service's `RestClientConfig` uses `ClientHttpRequestFactoryBuilder.detect().build(ClientHttpRequestFactorySettings.defaults().withConnectTimeout(...).withReadTimeout(...))`. Same behaviour, the timeout tests from branch 03 still pass.

**Flyway 10+ split database support into modules**: sentiment-service, video-service, and analytics-service add `flyway-database-postgresql` next to `flyway-core`. Without it a Flyway 11 service fails at start-up against Postgres with "Unsupported Database: PostgreSQL". H2 (used by the tests) is still built in.

**Spring Cloud Gateway 4.3** renamed its starter; api-gateway depends on `spring-cloud-starter-gateway-server-webflux`. The route and `httpclient` properties keep their existing `spring.cloud.gateway.*` names, which 4.3 still honours (`spring.cloud.gateway.server.webflux.*` is the new namespace and the reviewer may prefer to move to it in a follow-up; the routing integration tests are the proof either way).

**Graceful shutdown and structured logs, on by default.** Compose gives every Java service `stop_grace_period: 30s`, longer than the 20 s Spring phase timeout, so `docker compose stop` cannot cut the drain short at Compose's 10 s default. Each Java service also passes `STREAMSENSE_LOG_FORMAT` through (`${STREAMSENSE_LOG_FORMAT-ecs}`, which keeps an explicitly empty host value), so `STREAMSENSE_LOG_FORMAT= docker compose up -d <service>` really does restore plain text. The shared config-repo `application.yml` (and the eureka-server and config-server bootstrap files, which do not use config-repo) set `server.shutdown: graceful` with `spring.lifecycle.timeout-per-shutdown-phase: 20s`, and `logging.structured.format.console: ${STREAMSENSE_LOG_FORMAT:ecs}`. Every Java service now logs one Elastic-Common-Schema JSON object per line with `correlationId`, `traceId`, and `spanId` carried automatically; set `STREAMSENSE_LOG_FORMAT=` (empty) to get the previous plain-text pattern back for local reading. Compose healthchecks and CI probe HTTP, not log text, so nothing downstream parses these lines.

**Layered, non-root Dockerfiles for all eight services.** The heap is sized with `-XX:MaxRAMPercentage=75.0` on the `java` command line rather than through `JAVA_TOOL_OPTIONS`, whose "Picked up" banner would be the one non-JSON line on the console; the Kubernetes manifests no longer set that variable either and inherit the flag from the image. Two-stage build on the digest-pinned Temurin 21 JRE: the first stage runs `java -Djarmode=tools -jar application.jar extract --layers`, the second copies `dependencies/`, `spring-boot-loader/`, `snapshot-dependencies/`, and `application/` as separate layers (a code change no longer re-pushes the dependency layer), creates a system user with uid 10001 (the uid the Kubernetes `securityContext` from branch 04 already runs as), sets `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0`, declares `EXPOSE`, and runs `java -jar application.jar` as that user. A `.dockerignore` limits the build context to the jar.

## Deliberately left alone

- Boot 4.x exists (4.2.0-M1 is the newest tag on Maven Central). It is a major with its own migration guide; 3.5 is the supported target for this repository right now.
- The gateway property namespace (`spring.cloud.gateway.server.webflux.*`) is not adopted, to keep this diff to the upgrade itself.
- `@MockBean`/`@SpyBean` deprecation warnings (replaced by `@MockitoBean` in Boot 3.4) are left for a test-hygiene pass; they are warnings, not failures.
- No Buildpacks. The layered Dockerfile keeps the existing `build: ./<service>` Compose flow and the `target/*.jar` contract that `make package` relies on.

## Verification

| Check | Command | Result |
|---|---|---|
| Full reactor build and tests on 3.5 | `mvn -B -ntp -Dmaven.gitcommitid.skip=true clean verify` at the root in `maven:3.9-eclipse-temurin-21` | BUILD SUCCESS for all nine reactor entries. The run was split by an interruption: modules 1 to 7 (parent through video-service) in the first run, recommendation-service and analytics-service in a `-pl` rerun, and eureka-server plus config-server verified once more after a YAML fix (the first cut of this branch had inserted a second top-level `server:`/`spring:` block into their bootstrap files, which Boot's YAML loader rejects; the keys are now merged). 134 tests, 0 failures, same per-module counts as branch 07 |
| Layered image builds and runs non-root | `docker build` one Java service from its packaged jar; `docker run … id` prints uid 10001; `/actuator/health` answers | eureka-server image builds with five separate `COPY` layers; `/actuator/health` returns `{"status":"UP","groups":["liveness","readiness"]}`, both probe endpoints return 200, and `id` inside the container prints `uid=10001(app)` |
| Structured log shape | first log line of that container is a JSON object with `log.level`, `message`, `service.name`-style ECS keys | first line is `{"@timestamp":…,"log":{"level":"INFO","logger":…},"process":{"pid":1,…},"service":{"name":"eureka-server","version":"0.0.1-SNAPSHOT"},"message":…}` |
| Compose renders | `docker compose config -q` | OK |

## Manual checks for the reviewer

1. `make up`: every service healthy; `docker compose logs sentiment-service | head -3` shows JSON lines; `STREAMSENSE_LOG_FORMAT= docker compose up -d sentiment-service` restores plain text.
2. `docker compose exec api-gateway id` prints `uid=10001(app)`.
3. `docker compose stop sentiment-service` while chat is flowing: the log shows "Commencing graceful shutdown" and the Kafka listener containers stopping before the JVM exits; no ingested message is lost (counts in `/api/sentiment/recent` match).
4. `curl localhost:8083/actuator/info` shows `build.version` and, from a real checkout, `git.commit.id.abbrev`.
5. `make replay-smoke` passes end to end.
6. On kind: pods start under the existing `runAsUser: 10001` security context without an image user mismatch.

## Follow-ups (not in this branch)

- Adopt `spring.cloud.gateway.server.webflux.*` property names.
- Replace `@MockBean` with `@MockitoBean` in tests.
- Tracing: Boot 3.5's `spring-boot-starter-opentelemetry` path (OTLP) instead of Brave and Zipkin reporter, once an OTel collector is part of the stack.
