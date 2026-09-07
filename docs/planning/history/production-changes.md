# Key Architecture Changes For Production Port of Streamsense Project
## Zuul → Spring Cloud Gateway

### Rationale
- Zuul 1 is legacy and not the default gateway choice in modern Spring Cloud stacks.
- Spring Cloud Gateway is the standard successor for routing/filtering in Spring Cloud.
- Keeps the “API gateway” concept identical: centralized routing, cross-cutting concerns.
---

## Hystrix → Resilience4j

### Rationale
- Hystrix is deprecated and not actively maintained in modern Spring ecosystems.
- Resilience4j integrates cleanly with Spring Boot and supports circuit breaker, retry, bulkhead patterns.
---