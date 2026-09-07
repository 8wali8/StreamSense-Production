# hardening/09-problem-details

Priority 8 from `docs/planning/production-hardening.md`: errors leave the Java services in one documented shape. Stacked on `hardening/08-boot-upgrade`.

## What changed

**One `web/GlobalExceptionHandler` in each of the five REST services** (chat, sentiment, video, analytics, recommendation). It is a `@RestControllerAdvice` that extends Spring's `ResponseEntityExceptionHandler`, so every framework-raised error (unreadable JSON, missing parameter, unsupported media type, `ResponseStatusException`, bean-validation failure on a `@RequestBody`) already arrives as an RFC 9457 `application/problem+json` body; the class adds:

- `IllegalArgumentException` → 400 `…/problems/invalid-request` with the message as `detail`. This is what `VideoController` throws for an oversized `frameRef` and what `MetricQueryService` throws for a bad window; both used to surface as whitelabel 500s.
- `IllegalStateException` → 409 `…/problems/conflict` (for example "Twitch chat ingestion is disabled").
- `ConstraintViolationException` (method-parameter validation such as `@Min`/`@Max` on `limit`) → 400 `…/problems/validation-failed` with an `errors[]` list of `field` and `message`; `MethodArgumentNotValidException` gets the same `errors[]` on top of the framework's body.
- Anything else → 500 `…/problems/internal-error` with a fixed `detail`; the exception is logged with its stack trace and its message never reaches the client.
- The framework's own problems (malformed JSON, a missing parameter, an unsupported media type, a wrong method, an unknown path) arrive typed `about:blank`; the advice gives them a StreamSense type (`malformed-request`, `missing-parameter`, `unsupported-media-type`, `method-not-allowed`, `not-found`, `invalid-request`, or the status phrase) before decorating them, so clients can tell the categories apart by `type`.

Every problem also carries `instance` (the request path), `service` (from `spring.application.name`), `timestamp`, and the request's `correlationId` from MDC, so a client can quote a failure back and it can be found in the logs.

`TwitchChatStatusController` drops its hand-written `try/catch` that translated the same two exceptions into `ResponseStatusException`; the advice does it uniformly now.

**The gateway's own WebFilter rejections are problem details too.** The rate limiter's 429 and the auth filter's 401 commit their responses before WebFlux's problem-details support can run, so a small `ProblemResponses` helper writes the same `application/problem+json` shape (`type`, `title`, `status`, `detail`, `instance`, `service`, `timestamp`, `correlationId`) from the filters; the previous `error`, `limit`, and `reason` members stay for existing clients.

**The gateway maps resolver failures to GraphQL errors with a stable code** (`graphql/GraphQlErrorAdvice`, `@GraphQlExceptionHandler`): `WebClientRequestException` (connection refused, DNS, the response timeout from branch 03) → `extensions.code = DOWNSTREAM_UNAVAILABLE` with the host; `WebClientResponseException` with a 5xx → `DOWNSTREAM_ERROR` with the host and the HTTP `status`; a 4xx from a downstream service (the services validate `limit`, `windowMinutes`, `bucketSeconds`, and blank streamers themselves) → `BAD_REQUEST` with the host and status, because it is the caller's mistake rather than a service fault; validation and `IllegalArgumentException` → `BAD_REQUEST`. Messages are fixed strings; downstream response bodies are never echoed. Before this, any downstream failure was an unexplained `INTERNAL_ERROR` that the frontend could only render as "something went wrong".

**Shared config**: `spring.mvc.problemdetails.enabled` and `spring.webflux.problemdetails.enabled` are on in config-repo `application.yml`, so the gateway's REST routes (which have no advice of their own) also answer with problem+json for framework errors.

**Tests**: a `GlobalExceptionHandlerTest` per service (the four mappings, `service`/`instance`/`correlationId` decoration, no message leak on 500), `VideoControllerProblemDetailTest` and `TwitchChatStatusControllerProblemDetailTest` through MockMvc with the real controllers, and `GraphQlErrorAdviceTest` against a `MockWebServer` returning 503 and a closed port.

## Deliberately left alone

- No new domain exception hierarchy. `IllegalArgumentException`/`IllegalStateException` are what the code base already throws at the boundaries, and the mapping is documented in CLAUDE.md; typed exceptions can replace them incrementally.
- `type` URIs under `https://streamsense.dev/problems/` are identifiers, not links to hosted pages yet.
- The frontend does not yet read `extensions.code`; branch 11c can show "sentiment-service unavailable" instead of a generic error once it does.

## Verification

| Check | Command | Result |
|---|---|---|
| Full reactor build and tests | `mvn -B -ntp -Dmaven.gitcommitid.skip=true clean verify` at the root in `maven:3.9-eclipse-temurin-21` | BUILD SUCCESS, all 8 modules (parent, eureka-server, config-server passed on the first run; api-gateway onward re-run with `-rf :api-gateway` after fixing a test compile error in `GraphQlErrorAdviceTest`), 0 failures |
| Compose renders | `docker compose config -q` | OK |

## Manual checks for the reviewer

1. `make up`, then `curl -i 'localhost:8083/api/sentiment/recent?streamer=x&limit=999'`: 400 with `Content-Type: application/problem+json`, `type` ending in `validation-failed`, and `errors[0].field` = `recent.limit`.
2. `curl -i -X POST localhost:8081/api/chat/twitch/channels -H 'Content-Type: application/json' -d '{"channels":[]}'`: 400 problem with `detail` "at least one Twitch channel is required".
3. `curl -i 'localhost:8085/api/analytics/streams/x/timeseries?bucketSeconds=30'`: 400 problem (not a 500).
4. `docker compose stop recommendation-service`, then run the `recommendations` GraphQL query: the response has `errors[0].extensions.code = "DOWNSTREAM_UNAVAILABLE"`.
5. Every problem body includes `service` and, when the request sent `X-Correlation-Id`, the same `correlationId`.

## Follow-ups (not in this branch)

- Frontend: surface `extensions.code` per panel (branch 11c).
- Typed domain exceptions where a 404 or 422 would be more accurate than 400/409.
