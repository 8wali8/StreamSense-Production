# fix/helix-rate-limit

Step 4 of `docs/planning/handoffs/helix-poller.md`, first item: what analytics-service does when Twitch answers 429. Based on `main` after the poller was proven against the real API on the VM (2026-09-10).

## What was missing

Helix budgets requests per application: 800 points a minute, visible in the `Ratelimit-Limit`, `Ratelimit-Remaining`, and `Ratelimit-Reset` headers of every answer. The client handled 401 (refresh the app token and retry once) and nothing else, so a 429 surfaced as a generic `HttpClientErrorException`: the poller logged it and asked again a minute later, `VodImportService.list` turned it into a 500 "An unexpected error occurred" for the import panel, and, unnoticed, Apache HttpClient's default retry strategy had already resent every 429 after one second, spending more of the budget the answer said was gone.

## What changed

- **`TwitchHelixClient`** pauses after a 429: the reset time comes from `Ratelimit-Reset` (epoch seconds, Twitch's documented header) or `Retry-After` (seconds) when present, in the future, and at most five minutes away; otherwise the new `rate-limit-backoff-ms` (default 60 s). Until then every call, streams, users, or videos, throws `HelixRateLimitedException` without sending a request. The 429 is logged once at warn with the resume time. `GET /streams` now goes through the same `get` path as the other endpoints (one place handles 401, 429, and the single retry of a dropped connection). The client takes a `Clock`; the three-argument constructor keeps the system clock.
- **`StreamSessionPoller`** catches the new exception at debug: open sessions keep their last sample until a poll gets through, and nothing is closed on the strength of an answer that never came.
- **`GlobalExceptionHandler`** maps it to 503 `twitch-rate-limited` with a `Retry-After` header and a detail the import panel shows as is ("Twitch is rate limiting this application; try again in N seconds"), instead of the 500 an unexpected exception gets.
- **`TwitchHelixConfig`** builds the Twitch request factory explicitly (`twitchRequestFactory`): the same connect and read timeouts as before, plus `disableAutomaticRetries()` on the Apache client. The Helix client already retries a dropped connection once itself; the automatic retry only mattered for 429 and 503, where it worked against the pause.
- **`config-server/config-repo/analytics-service.yml`**: `streamsense.twitch.helix.rate-limit-backoff-ms` (`STREAMSENSE_TWITCH_HELIX_RATE_LIMIT_BACKOFF_MS`, default 60000).

## Verification

- `TwitchHelixClientTest`: a 429 with `Ratelimit-Reset` pauses the client (one request on the wire, then none for streams or archives until a hand-moved clock passes the reset, then one more); the reset-time table (header, `Retry-After`, past, an hour away, unparsable, fallback) and the `Retry-After` rounding. The pause test uses the production request factory, because with the default one the test saw two requests: Apache's retry, which is how the problem was found.
- `StreamSessionPollerTest`: a rate-limited client closes nothing and the next poll still runs.
- `GlobalExceptionHandlerTest` and `VodImportTest`: the 503, its type, detail, and `Retry-After`, unit and through MockMvc.
- `mvn verify` on analytics-service from WSL (Maven 3.9.16, `-Dmaven.gitcommitid.skip=true` because the plugin cannot read a Windows worktree's gitdir): 40 tests, Spotless and ArchUnit clean, JaCoCo floor met. `kubectl kustomize .` and a strict duplicate-key parse of the config file.
- Review round (Codex, two P2s, both fixed): the clock for a relative `Retry-After` is read when the 429 arrives, not before the request and a possible token fetch; and the pause deadline is an atomic max, because the poller and import requests share the client and a late 429 with an earlier reset must not shorten a pause another answer set. `thePauseCountsFromTheAnswerAndOnlyEverMovesLater` covers both. 41 tests after the round.
- Not observed against the real API: Twitch has not returned a 429 to this application (one poll a minute for one to a few dozen logins is far below 800 points). The header names and the epoch-seconds format of `Ratelimit-Reset` were read off real 200 answers on 2026-09-10.

## Deliberately left alone

- **No retry inside the request.** Waiting out the reset on the scheduler thread or the request thread would block a poll or an HTTP request for up to a minute; failing fast and letting the next poll or the user's retry button try again is the simpler contract.
- **No per-endpoint budgets.** One pause covers streams, users, and videos, because Twitch's budget is per application, not per endpoint.
- **Unknown logins and the VOD end-time P2** are the next items of step 4, each its own branch.
