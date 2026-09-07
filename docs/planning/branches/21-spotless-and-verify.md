# hardening/21-spotless-and-verify

Item 21 of `docs/planning/production-hardening-followups.md` (follow-ups from branches 07, 08, and 13): the Java sources are formatted once, the formatter becomes a gate, CI runs the full `verify` lifecycle, and two deprecations from the Boot 3.5 / Spring Cloud 2025 upgrade are cleared. Stacked on `hardening/20-followups-plan`.

## What changed

- **`style:` commit**: `mvn spotless:apply` over every module with the parent's palantir-java-format configuration and unused-import removal. 256 files, formatting only, kept as its own commit so `git blame` and the next commit stay readable.
- **Spotless is a gate**: the parent POM binds `spotless:check` to `verify`, so an unformatted file fails the build locally and in CI; `mvn spotless:apply` fixes it. The pre-commit config from branch 14 does not run Spotless (it needs Maven), so the CI step is the enforcement point.
- **CI runs `verify`, not `test`**: the Java matrix now executes JaCoCo report generation, the enforcer, and the Spotless check on every PR; before this only `test` ran and the `verify`-bound plugins were exercised only by hand.
- **`@MockBean`/`@SpyBean` → `@MockitoBean`/`@MockitoSpyBean`** in the four tests that used them (Boot 3.4 deprecated the old annotations; they were producing warnings on every build).
- **Gateway property namespace**: `spring.cloud.gateway.{routes,httpclient,metrics}` → `spring.cloud.gateway.server.webflux.*` (the Spring Cloud Gateway 4.3 names; the old ones are flagged `deprecated` in the plugin's configuration metadata) in config-repo, the gateway's test config, the four routing integration tests, and the javadoc that cites them. CLAUDE.md names the new namespace.

- **Merged the review chain**: the reviewed and forward-merged state of `hardening/14-supply-chain` (secret generation, k8s claim fixes, graceful-shutdown grace periods, `ci-ok` aggregate) is merged in; the `ci-ok` job now also waits for `schema-compat`, `dockerfile-lint`, `security-scan`, and `sbom`, so branch protection covers the jobs branches 10 and 14 added; the new `ConfigRepoYamlTest` is formatted.

## Deliberately left alone

- No formatter for YAML, Markdown, or the Python services here: Prettier (frontend), ruff format (Python), and the pre-commit whitespace hooks already cover those.
- `AGENTS.md` still suggests `mvn -B -ntp clean test` as the quick per-service check; it remains valid, `verify` is the CI contract.

## Verification

| Check | Command | Result |
|---|---|---|
| Full reactor with the Spotless gate | `mvn -B -ntp -Dmaven.gitcommitid.skip=true clean verify` at the root in `maven:3.9-eclipse-temurin-21` | BUILD SUCCESS on all 9 modules before the review-chain merge; after the merge `spotless:check` across all modules and `config-server` verify (9 tests, which include the new `ConfigRepoYamlTest`) pass |
| Gate really fails on an unformatted file | append a badly indented method to one class, run `verify` on that module | proven in passing: the first full run failed chat-service with `format violations` on `ChatIngestControllerTest.java` (an import left out of order by the `@MockitoBean` replacement); `spotless:apply` fixed it and the rerun passed |
| Old namespace gone | `grep -rn "spring.cloud.gateway\." --include=*.java --include=*.yml . \| grep -v server.webflux` | no matches |
| Compose renders (config-repo changed) | `docker compose config -q` | OK |
| Kubernetes renders (config-repo feeds the ConfigMap) | `kubectl kustomize .` | OK |
| Workflow syntax | `actionlint .github/workflows/ci.yml` | OK |

## Manual checks for the reviewer

1. `git show --stat <style commit>` touches only `*.java`; `git diff <style commit>^ <style commit> -w --stat` is close to empty (whitespace-only reformatting apart from import removal and line wrapping).
2. Introduce a formatting error and run `mvn -pl chat-service verify`: the build fails naming the file; `mvn spotless:apply` fixes it.
3. `make up`: the gateway routes still resolve (`curl localhost:8080/api/chat/twitch/status`), proving the new property namespace is honoured.

## Follow-ups

- None new. Branch 26 adds ArchUnit and a JaCoCo floor on top of the `verify` lifecycle this branch makes CI run.
