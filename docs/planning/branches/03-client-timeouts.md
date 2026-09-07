# hardening/03-client-timeouts

Priority 3 from `docs/planning/production-hardening.md`: wiring fails fast and every outbound call is bounded. Stacked on `hardening/01-secrets` because both edit the six bootstrap `application.yml` files (the plan listed `main`; stacking avoids a guaranteed conflict).

## What changed

**api-gateway: required downstream URLs, one timeout policy.** A new validated `DownstreamServicesProperties` (`streamsense.services.*`) binds all six base URLs as `@NotBlank` plus `connect-timeout` (2 s) and `response-timeout` (5 s). The four resolver clients take the properties bean instead of `@Value` strings; `RecommendationServiceClient` and `AnalyticsServiceClient` therefore lose their `http://localhost:808x` fallbacks. `DownstreamWebClientConfig` registers a `WebClientCustomizer` that gives every `WebClient` built from the auto-configured builder a reactor-netty connect timeout and response timeout, so a stalled sentiment, video, analytics, or recommendation service fails the GraphQL field instead of holding the subscriber forever. Proxied routes get `spring.cloud.gateway.httpclient.connect-timeout: 2000` and `response-timeout: 10s` in config-repo.

**recommendation-service: same idea with `RestClient`.** `StreamSenseProperties` is `@Validated`, both `base-url`s are `@NotBlank`, and `Services` gains `connect-timeout-ms` (2000) and `read-timeout-ms` (5000). The two history clients drop their localhost ternaries. `RestClientConfig` registers a `RestClientCustomizer` that installs a request factory with those timeouts.

**Config client is required, bounded, and retried.** All six bootstrap files now import `configserver:` (not `optional:configserver:`) with `request-connect-timeout: 5000`, `request-read-timeout: 10000`, `fail-fast: true`, and `retry` (2 s initial, ×1.5, 10 s cap, 12 attempts, roughly a minute). A service that cannot reach config-server now retries and then exits instead of starting with empty configuration and failing later in confusing ways. `spring-retry` is added to the six POMs, and `spring-boot-starter-aop` to the four that lacked it (retry needs AOP); sentiment-service and video-service already had AOP through Resilience4j.

**Tests.** `DownstreamServicesPropertiesTest` and `StreamSensePropertiesValidationTest` prove a missing base URL fails context startup with the offending property in the message. `DownstreamWebClientTimeoutTest` and `DownstreamRestClientTimeoutTest` prove, against a `MockWebServer` that delays its headers, that a 200 ms timeout produces `WebClientRequestException` (cause `ReadTimeoutException`) and `ResourceAccessException` (cause `SocketTimeoutException`) respectively, and that a responsive server still succeeds. analytics-service gains the `src/test/resources/application.yml` (config-server and Eureka disabled) that every other Java service already had; without it, its tests loaded the bootstrap file and hit the now-required config-server import. Both the gateway and recommendation test `application.yml` files carry every base URL so existing `@SpringBootTest`s pass validation; tests that override a URL via `@DynamicPropertySource` still do.

**Docs.** CLAUDE.md and AGENTS.md state the rules: no localhost defaults for service URLs, no HTTP client without timeouts, config-server import is required and retried.

## Deliberately left alone

- sentiment-service and video-service already bound their `RestTemplate` from `streamsense.ml.connect-timeout-ms` and `read-timeout-ms`; chat-service's Twitch VOD client already sets `connectTimeout` and a per-request `timeout`. Neither changed.
- No Resilience4j `TimeLimiter` in the gateway. The reactor-netty response timeout bounds the same thing without adding a dependency to the gateway; if per-call policies (circuit breaker, fallback) are wanted later, that is the place to add them.
- Timeout values are conservative defaults, not tuned numbers. The analytics summary is the slowest call today; if 5 s proves tight under a real window size, raise `streamsense.services.response-timeout` in config-repo rather than in code.

## Verification

| Check | Command | Result |
|---|---|---|
| kustomize renders (ConfigMap mirror edited) | `kubectl kustomize k8s` | OK |
| recommendation-service | `mvn -B -ntp clean test` in `maven:3.9-eclipse-temurin-21` | 9 passed (4 new) |
| chat-service | same | 25 passed |
| api-gateway | same | 62 passed (57 existing plus 5 new) |
| sentiment-service, video-service, analytics-service (bootstrap and POM change only) | same | sentiment-service 16, video-service 12, analytics-service 8, all pass (analytics needed the new test config described above) |

## Manual checks for the reviewer

1. `make up` (or `start-stack.ps1`): every service reaches healthy. The gateway and recommendation-service now refuse to start without their URLs, so any environment that relied on the localhost fallbacks will show it immediately.
2. `docker compose stop config-server` then `docker compose restart chat-service`: chat-service logs show retry attempts and the container exits after about a minute instead of reporting healthy with empty config. `docker compose up -d config-server chat-service` recovers it.
3. `docker compose stop recommendation-service`, then run the `recommendations` GraphQL query: it fails within a few seconds (connect refused, or 5 s timeout if the container is paused with `docker pause` instead of stopped) rather than hanging.
4. `make replay-smoke` passes.

## Follow-ups (not in this branch)

- Branch 09 turns these timeout failures into RFC 9457 `ProblemDetail` responses and GraphQL errors with a stable `extensions.code`.
- Branch 07 (parent POM) centralises the `spring-retry` and AOP declarations added here.
