# hardening/12b-redis-rate-limit

Priority 11 from `docs/planning/production-hardening.md`: rate limits that hold when the gateway has more than one replica. Stacked on `hardening/12a-nginx`.

## What was wrong

`InMemoryRateLimiter` counted requests in a `ConcurrentHashMap` inside each gateway process. One replica enforces the configured limit; three replicas behind a load balancer enforce three times it, and a rolling restart resets every counter. The limiter was also the only `@Component` of its kind, so there was no seam to put anything else behind the filter.

## What changed

- **`ratelimit/RateLimiter`**: the interface the web filter depends on, reactive (`Mono<RateLimitDecision>`) so a store round-trip never blocks a Netty thread. The filter's headers, 429 body, `Retry-After`, and rejection metric are unchanged.
- **`ratelimit/RedisRateLimiter`**: one key per rule, client, and window (`streamsense:ratelimit:<rule>:<client>:<windowStart>`), incremented and given its TTL in a single Lua script so two replicas racing on a window's first request cannot leave a key without an expiry; keys expire one second after the window closes, however late in the window they were created (the TTL is `resetAt - now + 1`, not a full window). Uses Spring Data Redis Reactive (Lettuce) with 500 ms connect and command budgets from config. When Redis is unreachable the limiter fails open by default (`streamsense.gateway.rate-limit-fail-open=true`: an edge limiter outage should not take the site down) or closed when configured, and every store error is counted in `streamsense_gateway_rate_limit_store_errors_total` and logged.
- **`ratelimit/InMemoryRateLimiter`** stays as the `memory` store for local runs and tests, now behind the interface and no longer a `@Component`; `config/RateLimitStoreConfig` registers exactly one store from `streamsense.gateway.rate-limit-store` (`redis` in config-repo for Compose and Kubernetes, `memory` in the gateway's test config) and logs which one, with a warning for `memory`.
- **Wiring**: `spring.data.redis.host/port` (`REDIS_HOST`, `REDIS_PORT`, default `redis`) in config-repo; the Compose gateway waits for the healthy `redis` service; the Compose and Kubernetes gateway definitions pass `STREAMSENSE_GATEWAY_RATE_LIMIT_STORE`, `STREAMSENSE_GATEWAY_RATE_LIMIT_FAIL_OPEN`, `REDIS_HOST`, and `REDIS_PORT` through, so the documented overrides (`STREAMSENSE_GATEWAY_RATE_LIMIT_STORE=memory make up`) reach the container. There is deliberately no `wait-for-redis` init container and Redis is not in the readiness probe: the store is fail-open, so a gateway must be able to start and be rescheduled while Redis is down, exactly as a running one keeps serving.
- **Tests**: `InMemoryRateLimiterTest` keeps its window and eviction cases plus the reactive path; `RedisRateLimiterTest` runs against the same digest-pinned `redis:7.4.11-alpine` image through Testcontainers (skipped without Docker) and covers overflow within a window, separate clients and windows, key TTL, and fail-open versus fail-closed against a closed port with the error counter; `RateLimitStoreConfigTest` proves the default store, the Redis store when configured, and that only one is ever registered. The three existing rate-limit integration tests run unchanged on the memory store.
- **CLAUDE.md** describes the store selection and fail-open behaviour.

## Deliberately left alone

- Spring Cloud Gateway's built-in `RequestRateLimiter` + `RedisRateLimiter` (token bucket per route) was not adopted. Its auto-configuration still activates once reactive Redis is on the classpath and registers a bean named `redisRateLimiter`, so the store beans here are named `redisRateLimitStore` and `inMemoryRateLimitStore`; the first cut used the clashing name and the gateway refused to start under Compose (bean overriding is disabled), which the docker-smoke job caught; `RateLimitStoreConfigTest` now registers a stand-in bean with the gateway's name (the auto-configuration class is package-private and needs the whole gateway context) and asserts both coexist with overriding disabled. Reason it was not adopted: the existing filter already implements method-and-path rules, trusted-proxy keying, RFC-style headers, and a JSON 429 with tests for all of it, and the built-in filter would replace those semantics rather than add to them. The Redis store gives the missing property (shared counters) without rewriting the contract.
- Fixed windows, not sliding: the semantics the tests and headers already promise. A sliding window is a script change behind the same interface.
- Redis is not made part of the gateway's readiness: with fail-open, a Redis outage degrades limiting rather than availability. Set `rate-limit-fail-open=false` and add Redis to the readiness group together if the opposite trade-off is wanted.

## Verification

| Check | Command | Result |
|---|---|---|
| Gateway build and tests | `mvn -B -ntp -Dmaven.gitcommitid.skip=true -pl api-gateway -am clean verify` in `maven:3.9-eclipse-temurin-21` with the Docker socket mounted (`TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`) | BUILD SUCCESS, 77 tests, 0 failures, 0 skipped |
| Redis test really ran | `RedisRateLimiterTest` in the surefire report | `RedisRateLimiterTest`: 4 tests run (not skipped), 5.2 s, against `redis:7.4.11-alpine` started by Testcontainers |
| Compose renders | `docker compose config -q` | OK |
| Kubernetes renders and validates | `kubectl kustomize .`, `kubeconform -strict`, `kube-linter lint k8s/apps/api-gateway.yaml` | 54 resources valid; `no-read-only-root-fs` findings unchanged from before this branch on the existing containers, none on the new `wait-for-redis` init container |

## Manual checks for the reviewer

1. `make up`, then `for i in 1 2 3; do curl -s -o /dev/null -w '%{http_code} ' -X POST localhost:8080/api/chat/ingest -H 'Content-Type: application/json' -d '{"streamer":"t","user":"u","message":"m","timestamp":1}'; done` (with the chat-ingest limit lowered to 2 in config-repo): `200 200 429`. Then `docker compose exec redis redis-cli keys 'streamsense:ratelimit:*'` shows the bucket with a TTL.
2. `docker compose stop redis`, repeat the requests: they pass (fail-open) and `curl -s localhost:8080/actuator/prometheus | grep rate_limit_store_errors` increments; the gateway log shows one warning per request.
3. `STREAMSENSE_GATEWAY_RATE_LIMIT_STORE=memory make up`: the gateway logs the per-instance warning at startup and limits still work for a single instance.
4. Scale the gateway to two replicas on kind: the limit holds across both pods.

## Follow-ups

- Sliding-window or token-bucket script if burst shaping is wanted.
- Include Redis in readiness if fail-closed is ever chosen.
