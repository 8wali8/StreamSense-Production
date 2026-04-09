# Resilience4j And Hystrix-Style Config Mapping

## Purpose

Sprint 4 uses Resilience4j for real runtime behavior, but keeps a compatibility-friendly `hystrix.*` shape in Config Server so the intent stays readable for anyone familiar with the earlier Hystrix model.

The `hystrix.*` keys are documentation and continuity aids.

The service runtime behavior is driven by the real `resilience4j.*` configuration.

## Current Sentiment ML Mapping

Compatibility section in `config-server/config-repo/sentiment-service.yml`:

```yaml
hystrix:
  command:
    mlSentiment:
      executionTimeoutMs: 3000
      requestVolumeThreshold: 10
      errorThresholdPercentage: 50
      sleepWindowMs: 15000
      maxConcurrentCalls: 4
      maxRetries: 2
```

Actual runtime section:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      mlSentiment:
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        waitDurationInOpenState: 15s
  retry:
    instances:
      mlSentiment:
        maxAttempts: 3
        waitDuration: 1s
  bulkhead:
    instances:
      mlSentiment:
        maxConcurrentCalls: 4
        maxWaitDuration: 0ms
```

## Practical Mapping Table

| Hystrix-style key | Resilience4j target | Notes |
|---|---|---|
| `executionTimeoutMs` | HTTP client connect/read timeouts | The current service uses explicit `RestTemplate` timeouts from config rather than a separate time limiter layer |
| `requestVolumeThreshold` | `minimumNumberOfCalls` / `slidingWindowSize` | Controls how much traffic is needed before the breaker state becomes meaningful |
| `errorThresholdPercentage` | `failureRateThreshold` | Same conceptual meaning |
| `sleepWindowMs` | `waitDurationInOpenState` | How long the breaker remains open before probing half-open behavior |
| `maxConcurrentCalls` | `bulkhead.maxConcurrentCalls` | Limits concurrent ML dependency calls |
| `maxRetries` | `retry.maxAttempts - 1` | Hystrix often implied fallback after failure; here retries are explicit |

## Current Fallback Contract

When ML dependency failures exhaust the protected path, `sentiment-service` emits a fallback sentiment result:

- `label = NEUTRAL`
- `score = 0.0`
- `modelVersion = fallback`

Fallback results are still persisted and published so the degraded path remains visible end to end.

## Current Non-Fallback Terminal Failures

The service does **not** fallback for everything.

Examples of terminal failures that should move toward retry/DLT instead of a neutral fallback:

- invalid or malformed ML response contract
- persistence failures after sentiment calculation
- unrecoverable processing defects

## Future `video-service` Reuse

`video-service` config already reserves the same shape for `mlSponsor` so Week 5 can reuse the same resilience conventions without inventing a second config style.
