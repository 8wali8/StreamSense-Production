# hardening/07-parent-pom

Priority 7 (part 1) from `docs/planning/production-hardening.md`: one parent POM for the eight Spring Boot services, with every version in one place and the build-quality plugins wired in. No dependency versions change in this branch; the Boot upgrade is branch 08. Stacked on `hardening/06b-video-capture-lifecycle`.

## What changed

**Root `pom.xml` (`com.streamsense:streamsense-parent`)** inherits from `spring-boot-starter-parent` 3.2.12, lists the eight services as `<modules>`, and manages versions:

- `spring-cloud-dependencies` 2023.0.4 and `resilience4j-bom` 2.2.0 as imported BOMs;
- `nimbus-jose-jwt` 9.37.3 in `dependencyManagement` (the one library no BOM covers);
- plugin versions for enforcer, JaCoCo, Spotless, and git-commit-id under `pluginManagement`.

**Service POMs shrink.** Each now has a three-line `<parent>` pointing at `streamsense-parent`, no `groupId`/`version` of its own, no `dependencyManagement`, and no `java.version` or `spring-cloud.version` properties. sentiment-service and video-service drop their hard-coded `2.2.0` on the two Resilience4j artifacts; api-gateway drops `9.37.3` on Nimbus. chat-service keeps its `mockito.version`/`byte-buddy.version` overrides and the surefire `argLine`, because those exist for a reason local to that module (Java 21 with Mockito's inline mocking) and changing them for all modules is not this branch's job.

**Plugins the parent runs for every module:**

- `maven-enforcer-plugin`: Java `[21,22)` and Maven `[3.9,)`, so the wrong toolchain fails immediately instead of with a compiler or plugin error later.
- `jacoco-maven-plugin`: `prepare-agent` on every test run and an HTML/XML `report` at `verify` (`target/site/jacoco`). No threshold yet; measure first.
- `spring-boot-maven-plugin` `build-info` (from `pluginManagement`; each service still declares the plugin) so `/actuator/info` reports the build.
- `git-commit-id-maven-plugin` `revision`, restricted to `git.commit.id.abbrev`, `git.commit.time`, `git.branch`, and configured with `failOnNoGitDirectory=false` and `failOnUnableToExtractRepoInfo=false` so a build from an export or a Docker bind mount without `.git` still succeeds. One case those flags do not cover: a git *worktree* whose `.git` file points at a path the build environment cannot see (this branch was verified from a worktree inside a container, where the pointer names a Windows path). For that case pass `-Dmaven.gitcommitid.skip=true`; a normal checkout needs nothing.
- `spotless-maven-plugin` with palantir-java-format is configured but bound to no phase. The sources are not formatted yet; `mvn spotless:apply` on the whole tree is a single mechanical commit that should not be mixed with anything else, after which `check` gets bound to `verify`.

**CI knows about the parent.** The `java-shared` and `smoke` path filters include the root `pom.xml`, so a version bump in the parent runs every Java suite and the Docker smoke instead of only the `changes` job. **Worktrees.** `tools/start-stack.ps1` detects a git worktree (`.git` is a file whose target sits outside the bind mount) and passes `-Dmaven.gitcommitid.skip=true` to the container build, which is the case the plugin's failure flags do not cover; `make package` runs Maven on the host, where the worktree resolves normally.

**One reactor build.** `make package` and `tools/start-stack.ps1` now run `mvn -DskipTests package` once at the root instead of eight sequential builds; Maven resolves shared dependencies once and can parallelise. `mvn -f <service>/pom.xml` still works (the parent is found through the default `relativePath`), so CI's per-service matrix and the path filtering from branch 02a are unchanged.

**Docs**: CLAUDE.md and AGENTS.md describe the multi-module build and the rule that versions live in the parent.

## Deliberately left alone

- Boot 3.2.12 and Spring Cloud 2023.0.4 stay exactly as they were. Branch 08 moves them.
- No ArchUnit yet; that needs a per-service test and a rules discussion, and is listed as a follow-up.
- Spotless is not enforced (see above).
- CI still runs `mvn clean test` per service, not `verify`. Switching to `verify` (which now also produces the JaCoCo report) is a one-word CI change worth making once branch 02a is merged, to avoid a workflow conflict.

## Verification

| Check | Command | Result |
|---|---|---|
| Full reactor build and tests | `mvn -B -ntp clean verify` at the root in `maven:3.9-eclipse-temurin-21` (enforcer, all eight modules, tests, JaCoCo reports) | BUILD SUCCESS, all nine reactor entries; 134 tests, 0 failures (the same per-service counts as before: 1, 1, 62, 25, 16, 12, 9, 8); JaCoCo reports written under `<service>/target/site/jacoco` for all eight modules (chat-service's surefire `argLine` now starts with `@{argLine}` so it appends to the agent line instead of replacing it; the first run produced seven reports) (run with `-Dmaven.gitcommitid.skip=true` because the verification checkout is a git worktree, see above) |
| Single-service build still works | `mvn -B -ntp -f recommendation-service/pom.xml -DskipTests package` in the same image | produces `recommendation-service/target/recommendation-service-0.0.1-SNAPSHOT.jar` with the parent resolved from the file system |
| Resolved versions unchanged | `mvn dependency:tree` spot check on sentiment-service for resilience4j and api-gateway for nimbus | `resilience4j-spring-boot3:jar:2.2.0` and `nimbus-jose-jwt:jar:9.37.3`, identical to before |
| Scripts parse | PowerShell parser on `tools/start-stack.ps1` | parses cleanly |

## Manual checks for the reviewer

1. `make up` (or `start-stack.ps1`): the single "Package Java services (one reactor build)" step succeeds and every service reaches healthy.
2. `curl localhost:8083/actuator/info` returns `build` and `git` sections (`git.commit.id.abbrev` is present when built from a real checkout, absent but harmless from a worktree or export).
3. Open `sentiment-service/target/site/jacoco/index.html` after `mvn verify`.
4. `mvn -f api-gateway/pom.xml dependency:tree | grep nimbus` shows 9.37.3, and the same for resilience4j 2.2.0 in sentiment-service.

## Follow-ups (not in this branch)

- Branch 08: Boot 3.5.x and Spring Cloud 2025.0.x, graceful shutdown, structured logging, layered non-root Dockerfiles.
- `mvn spotless:apply` as a standalone commit, then bind `spotless:check` to `verify`.
- ArchUnit rules per service and a JaCoCo minimum once baseline coverage is known.
- CI: `mvn clean verify` instead of `test`.
