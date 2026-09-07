# hardening/13-dead-code

Priority 12 from `docs/planning/production-hardening.md`: remove what nothing uses, stop tracking build output, gate the schema explorer, and put the planning paper trail in one place. Stacked on `hardening/12b-redis-rate-limit`. No behaviour change for a running stack except that GraphiQL is now opt-in.

## What changed

- **Debug beans removed**: `ConfigDebugRunner` in chat-service and sentiment-service were `CommandLineRunner`s whose only job was to log one property at startup. Nothing referenced them; the property they logged is visible through `/actuator/env` and config-server anyway.
- **Stale config removed**: the `hystrix:` blocks in `config-server/config-repo/sentiment-service.yml` and `video-service.yml` configured a circuit breaker that is not on any classpath (the services use Resilience4j through `@Retry`, and no `hystrix` dependency exists). Spring ignored the keys; readers did not.
- **Bytecode untracked**: seven `ml-engine/**/__pycache__/*.pyc` files (Python 3.13 bytecode, from before the `.gitignore` rule existed) are removed from the index. The ignore rule `**/__pycache__/` already covers them.
- **GraphiQL gated**: `spring.graphql.graphiql.enabled` now defaults to `false` and reads `STREAMSENSE_GATEWAY_GRAPHIQL_ENABLED`; Compose sets it to `true` for local exploration, Kubernetes leaves it off. The auth filter's exclusion of `/graphiql` stays so it works when enabled.
- **Planning history consolidated** under `docs/planning/history/` with an index in `docs/planning/README.md`: `plan.md` (roadmap), `production-plan.md`, `docs/current-state.md`, `docs/next-work.md`, `docs/performance-report.md`, `docs/production-changes.md`, `docs/documentation.md`, `plans/`, `productionportplans/`, `sprintplans/`, and `opencodeCommandHistory/` (39 renames, `git mv` so history follows). `CLAUDE.md`, `AGENTS.md`, `README.md`, `tools/smoke/README.md`, and the moved files' own cross-references point at the new paths; the maintained runbooks (`howtorun.md`, `kubernetes-kind.md`, `replay-runbook.md`, `degraded-path-proof.md`, `architecture.md`) stay in `docs/`.
- **CLAUDE.md** lists the two new toggles (`STREAMSENSE_GATEWAY_RATE_LIMIT_STORE`, `STREAMSENSE_GATEWAY_GRAPHIQL_ENABLED`) and points to `docs/planning/`.

## Deliberately left alone

- No broad "unused class" purge. A grep for classes never referenced by name only finds Spring beans wired by annotation, which is not evidence of dead code; anything genuinely dead surfaced during the earlier branches (three frontend components in 11c) was removed there.
- The session logs and old plans are moved, not deleted: they explain why several things look the way they do, and they cost nothing where they are now.
- `AGENTS.md` stays as the entry point for other agents; it now defers to `CLAUDE.md` for the maintained description instead of the replay-milestone snapshot.

## Verification

| Check | Command | Result |
|---|---|---|
| Services that lost a bean still build and test | `mvn -B -ntp -Dmaven.gitcommitid.skip=true -pl chat-service,sentiment-service -am clean verify` in `maven:3.9-eclipse-temurin-21` | BUILD SUCCESS: chat-service 35 tests, sentiment-service 27 tests, 0 failures |
| No stale references to moved files | `grep -rn "plans/\|plan\.md\|docs/next-work\|docs/current-state\|opencodeCommandHistory\|sprintplans\|productionportplans" --include=*.md --include=makefile --include=*.yml .` outside `docs/planning/` | none |
| No `hystrix` keys left | `grep -rn hystrix config-server/config-repo/` | none |
| Compose renders | `docker compose config -q` | OK |
| Kubernetes renders (config-repo feeds the ConfigMap) | `kubectl kustomize .` | OK |
| Bytecode gone from the index | `git ls-files \| grep -c __pycache__` | 0 |

## Manual checks for the reviewer

1. `make up`: `http://localhost:8080/graphiql` still opens (Compose sets the toggle); on kind, `curl -i gateway.streamsense.local/graphiql` is a 404.
2. `git log --follow docs/planning/history/roadmap-12-week.md` shows the file's history under its old name.
3. `docker compose logs chat-service | grep chatMessages` no longer shows the startup debug line.

## Follow-ups

- Bind the Spotless check to `verify` (branch 07 left it manual) once the sources are formatted in one dedicated commit.
