# hardening/26-archunit-and-coverage

Item 26 of `docs/planning/production-hardening-followups.md` (follow-up from branch 07): the structural rules the codebase already follows are now tests, a line-coverage floor per Java module stops coverage from sliding, and the Python services run a real ruff rule set, `ruff format --check`, and mypy in CI. Stacked on `hardening/25-network-policy`.

## What was wrong

CLAUDE.md described conventions (constructor injection, controllers behind services, one `web/GlobalExceptionHandler`) that nothing enforced; JaCoCo measured coverage on every build but no build ever failed on it; ruff ran with its default four rule families (`E4`, `E7`, `E9`, `F`) so the `B008`/`BLE001` suppressions already in the two `pyproject.toml` files were inert, formatting was checked only by the optional pre-commit hook (23 files were not formatted), and neither service had a type checker.

## What changed

### Java

- **`ArchitectureTest` in every service** (ArchUnit 1.5.0, `archunit-junit5`, version managed in the parent POM). The eight files are identical apart from the package, so the rules stay the same everywhere:
  - no field injection, no `java.util.logging`, no `System.out`/`System.err`/`printStackTrace` (ArchUnit's `GeneralCodingRules`);
  - the web layer (`..web..`, `..controller..`, `..api..`, `..graphql..`) never depends on `..persistence..` or `..repository..`;
  - persistence never depends on the web, service, kafka, or client layers;
  - only classes in `..web..`/`..controller..` may depend on classes there, so response types belong to `api`/`model`;
  - every `@RestControllerAdvice` lives in `..web..`.
  Rules use `allowEmptyShould(true)` because eureka-server and config-server have no such packages. Test classes are excluded from analysis.
- **Two findings, both fixed rather than suppressed**: chat-service's `ChatKafkaProducerConfig` injected `spring.kafka.bootstrap-servers` into a field; it is now a `@Value` parameter of the `@Bean` method and the template bean takes the factory as a parameter. analytics-service kept its ten response records in `web` next to the exception handler, so `MetricQueryService` depended on the web layer; they moved (with `git mv`) to `analyticsservice.api`, imports rewritten, `web` keeps only `GlobalExceptionHandler`. The other `@Autowired` uses in the codebase are on constructors and pass.
- **JaCoCo `check` at `verify`** in the parent POM: a `BUNDLE` rule on `LINE` `COVEREDRATIO` with minimum `${jacoco.minimum.line}`. The parent defaults the property to `0.00`; each service pins its own floor at its measured line coverage rounded down to a whole percent minus two points, with the measurement in a comment next to it:

  | Module | Measured line coverage (baseline run) | Floor |
  |---|---|---|
  | api-gateway | 83.8 % | 0.81 |
  | recommendation-service | 89.5 % | 0.87 |
  | video-service | 85.7 % | 0.83 |
  | analytics-service | 85.1 % | 0.83 |
  | sentiment-service | 73.1 % | 0.71 |
  | chat-service | 51.0 % | 0.49 |
  | eureka-server, config-server | 11.8 % (an application class and configuration) | 0.09 |

  The floor is a ratchet: raise it when a module's coverage grows, never lower it to make a build pass (the parent POM comment says so). chat-service's 51 % is the number to improve first; its Twitch IRC client and replay paths are the uncovered part.

### Python (ml-engine, video-capture-service)

- **`[tool.ruff.lint] select`** in both `pyproject.toml` files: `E, W, F, I, B, UP, SIM, C4, RUF, PT, BLE, S, N`. `N815` is ignored (event and API models keep the JSON contracts' camelCase field names); tests may `assert` and carry placeholder credentials and `/tmp` paths (`S101`, `S105`, `S108`); the FastAPI entry points may keep the API's camelCase parameter names (`N803`). Of the 26 findings the new families raised, 20 were fixed in code (composite `assert a and b` split, `try/except/else` assertions turned into `pytest.raises(..., match=...)`, `contextlib.suppress`, two exceptions renamed `ModelNotReadyError` and `TwitchStreamOfflineError`, an over-long log format string wrapped, and the SAM checkpoint download now refuses anything but `http(s)` before `urlretrieve`), and four carry a `# noqa` with the reason on the line (the `/tmp` default of the filesystem frame backend, the `streamSessionId` protocol property, the `subprocess.Popen` whose argv is built from config, and `CaptureState(str, Enum)` where `StrEnum` would change `str()` output in logs and snapshots).
- **`ruff format`** applied to both services (13 and 10 files) and `ruff format --check` added to both CI jobs.
- **mypy** (`>=1.20,<2`, dev group; `uv.lock` updated) with `[tool.mypy]`: the pydantic plugin, `check_untyped_defs`, `no_implicit_optional`, `warn_unused_ignores`, over `src/main/python`. The 11 errors it found were fixed, none ignored: `Optional` narrowing in the capture loop (the worker binds `storage` and `publisher` once and raises if either is missing, matching the guards in `start()` and `switch_channels()`), the `KeyedEvent` protocol declares its key fields as read-only properties so frozen dataclasses satisfy it, `switch_capture_channels` answers 503 instead of dereferencing a manager that has not started, a `str | None` was reusing a `str` variable in both settings readers, and `max(..., key=dict.get)` became a lambda. CI runs `uv run mypy` for both services; CONTRIBUTING lists the four local commands.

### Docs

- **CLAUDE.md**: the ArchUnit rules and the coverage floor in the Java section; the ruff families, format check, mypy, and the "a `# noqa` needs a reason" rule in the Python section.

## Deliberately left alone

- No cross-service ArchUnit module: a shared test-jar would be a ninth Maven module for one file; the template is small enough to keep in sync by hand, and CLAUDE.md says the eight copies are identical.
- No branch-coverage floor and no per-class rule: line coverage per module is the one number that is easy to explain and to raise.
- The pre-commit ruff hook stays at 0.16.6 while the services lock 0.16.3; CI uses the locked version, and the two agree on this code.
- mypy is not `strict`: `disallow_untyped_defs` would be a large annotation job across both services with no bug-finding payoff visible in this run.

## Verification

| Check | Command | Result |
|---|---|---|
| Baseline coverage measurement | `mvn -B -ntp -Dmaven.gitcommitid.skip=true clean verify` in `maven:3.9-eclipse-temurin-21` before any change | BUILD SUCCESS; the per-module numbers in the table |
| Full reactor with ArchUnit and floors | the same command after the changes (`-rf`/`-pl` resumes after Spotless wrapped two long lines; every module verified once with the final sources) | BUILD SUCCESS on all nine modules: 8 + 16 + 84 + 42 + 36 + 27 + 20 + 20 + 23 tests, 0 failures, 0 skipped (the Redis Testcontainers tests ran); `ArchitectureTest` 7/7 in each service; JaCoCo `check` passed everywhere at the floors above |
| ArchUnit finds real violations | first run before the two fixes | chat-service `noFieldInjection` failed on `ChatKafkaProducerConfig.bootstrapServers`; `onlyTheWebLayerDependsOnTheWebLayer` would fail on analytics-service without the move |
| Spotless gate | part of `verify` | clean after `spotless:apply` on the new test files |
| Python lint, format, types, tests | `uv sync --locked`, `uv run ruff check src/main/python src/test/python`, `uv run ruff format --check …`, `uv run mypy`, `uv run pytest` in `python:3.11.16-slim` with `uv 0.12.9` | ml-engine: ruff clean, format clean, mypy "no issues found in 12 source files", 72 passed / 1 skipped; video-capture-service: ruff clean, format clean, mypy "no issues found in 13 source files", 48 passed |
| Workflow syntax | `actionlint .github/workflows/ci.yml` | clean |

## Manual checks for the reviewer

1. `mvn -pl chat-service clean verify` from the repository root: `ArchitectureTest` runs 7 rules and the build ends with the JaCoCo `check` line; lower `jacoco.minimum.line` in `chat-service/pom.xml` to `0.60` and the same command fails with "Coverage checks have not been met".
2. Add `@Autowired private ObjectMapper mapper;` to any service class and run its tests: `noFieldInjection` names the field.
3. `cd ml-engine && uv sync --locked && uv run mypy && uv run ruff check src/main/python src/test/python && uv run ruff format --check src/main/python src/test/python`: all clean.
4. `curl -s localhost:8085/api/analytics/streams/<streamer>/summary` on a running stack: the response shape is unchanged by the package move (records are serialized by field name).

## Follow-ups

- Raise chat-service's floor once its Twitch client and replay paths have tests (item 07's original gap).
- Consider `disallow_untyped_defs` per module in mypy once the public functions carry annotations.
