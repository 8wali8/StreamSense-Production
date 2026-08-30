# hardening/01-secrets

Priority 1 from `docs/planning/production-hardening.md`: get every credential out of committed files.

## What changed

**Docker Compose** now declares five file-backed secrets (`POSTGRES_PASSWORD`, `STREAMSENSE_FRAME_STORAGE_ACCESS_KEY`, `STREAMSENSE_FRAME_STORAGE_SECRET_KEY`, `GRAFANA_ADMIN_PASSWORD`, `STREAMSENSE_GATEWAY_AUTH_HMAC_SECRET`) read from git-ignored `secrets/<NAME>` files and mounted at `/run/secrets/<NAME>`.

- postgres uses `POSTGRES_PASSWORD_FILE`; minio uses `MINIO_ROOT_USER_FILE` and `MINIO_ROOT_PASSWORD_FILE`; grafana uses `GF_SECURITY_ADMIN_PASSWORD__FILE`. All three images support the file form natively.
- sentiment-service, video-service, and analytics-service mount `POSTGRES_PASSWORD`; api-gateway mounts the HMAC secret and no longer receives it as an environment variable.
- ml-engine and video-capture-service receive `STREAMSENSE_FRAME_STORAGE_ACCESS_KEY_FILE` and `..._SECRET_KEY_FILE` instead of inline values.

**Spring services** import `optional:configtree:/run/secrets/` after the config-server import in every bootstrap `application.yml`. Each file under `/run/secrets` becomes a property named after the file, so the config-repo placeholder `${POSTGRES_PASSWORD}` resolves from the mounted file in Compose and from the environment variable in Kubernetes with no Java code change. The placeholder has no default, so a service with neither source fails at startup instead of connecting with a guessed password.

**config-repo** `sentiment-service.yml`, `video-service.yml`, and `analytics-service.yml` replace `password: streamsense` with `password: ${POSTGRES_PASSWORD}`; the Kubernetes ConfigMap mirror carries the same three edits.

**Python services** gain a small `_secret_env(name)` helper (one copy per service, since they share no package) that reads `<NAME>_FILE` first and `<NAME>` second, strips the value, and raises a clear `ValueError` when the file is missing. video-capture-service no longer defaults the S3 access and secret keys to `streamsense`; its existing `validate()` already rejects an S3 backend without credentials.

**Kubernetes** builds a `streamsense-secrets` Secret in the `streamsense` namespace with `secretGenerator` from git-ignored `k8s/secrets/streamsense.env` (with kustomize's content-hash suffix, so a changed value produces a new Secret name, kustomize rewrites every `secretKeyRef` to it, and the pods that use it roll). postgres, minio, grafana, ml-engine, video-capture-service, sentiment-service, video-service, analytics-service, and api-gateway read their credentials through `secretKeyRef`. The gateway previously referenced a secret named `api-gateway-auth` that nothing created; it now uses the generated one.

**Tooling and docs**: `make secrets` writes every missing secret file with a random value (`openssl rand -hex`, mode 0600; 16 hex chars for the MinIO access key, 64 for the HMAC secret, 32 otherwise) and writes `k8s/secrets/streamsense.env` with the same values; it is a prerequisite of `up`, `up-fast`, and `smoke-e2e`. `tools/start-stack.ps1` generates the Compose files the same way with `RandomNumberGenerator`. The committed `*.example` files are placeholders that document the file names. CI runs `make secrets` before `docker compose config` and before `kubectl kustomize`; `secrets/README.md`, `docs/howtorun.md`, `docs/kubernetes-kind.md`, `CLAUDE.md`, `AGENTS.md`, and `README.md` describe the new flow. `.gitignore` excludes `secrets/*` except the README and examples, and `k8s/secrets/*.env`.

## Deliberately left alone

- `GF_SECURITY_ADMIN_USER: admin`, `POSTGRES_USER`, and `POSTGRES_DB` stay literal. They are identifiers, not credentials.
- The Twitch client id in `config-repo/chat-service.yml` is Twitch's public anonymous web client id, not a secret.
- Existing secret files are never overwritten, so a stack that already has them keeps working. A clone that had none gets random values; the README explains how to keep an existing Postgres or MinIO volume (write its original password into the file first) or start over with `make nuke`.
- ml-engine translates a missing or unreadable `*_FILE` secret mount into `FrameArtifactError` when it builds the S3 client, so the sponsor and segmentation endpoints degrade the same way they do for any other frame-read failure instead of returning an unhandled 500.
- Config-server encryption (`{cipher}` values) was not adopted. The config repo is mounted from the working tree, so placeholders plus file secrets are simpler and equally effective here.

## Verification

Run on the branch at the commit under review, from a checkout with no `secrets/` directory.

| Check | Command | Result |
|---|---|---|
| Local secret files | `make secrets` in a copy with no secret files | five files plus `k8s/secrets/streamsense.env` created, mode 0600, lengths 32/16/32/32/64 hex chars; a second run creates nothing; `git check-ignore` confirms both locations are ignored |
| Generation failures | same copy with `openssl` removed from `PATH`; then with `COMPOSE_PROJECT_NAME` pointing at a project whose `postgres-data` volume exists and `secrets/POSTGRES_PASSWORD` deleted | both exit 1 with a one-line explanation and create no file (no zero-byte leftovers) |
| PowerShell generator | the `Ensure local secrets` step of `tools/start-stack.ps1` run against an empty `secrets/` copy in Windows PowerShell 5.1 | five files created with the same lengths; with an existing `postgres-data` volume for the project it throws the same legacy-volume message and creates nothing |
| Compose renders | `docker compose config -q` | OK; rendered output shows every credential as a `/run/secrets/<NAME>` mount and no inline values |
| kustomize renders | `kubectl kustomize k8s` | OK, 3251 lines; the only surviving `value: streamsense` and `value: admin` lines are `POSTGRES_DB`, `POSTGRES_USER`, and `GF_SECURITY_ADMIN_USER` |
| Generated Secret | grep of the rendered output | `streamsense-secrets-<hash>` in namespace `streamsense`, 5 keys; all 13 references carry the same hashed name and no unhashed reference remains |
| video-capture-service tests | `pytest src/test/python` in `python:3.11-slim` | 23 passed (4 new) |
| ml-engine tests | `pytest src/test/python/test_frame_store.py` in `python:3.11-slim` | 6 passed (4 new, including the missing-secret-file to `FrameArtifactError` case); the full suite needs torch and was not run locally, CI runs it |
| PowerShell | `Parser::ParseFile` on `tools/start-stack.ps1` | parses cleanly |
| Java suites | `mvn -B -ntp clean test` in `maven:3.9-eclipse-temurin-21` for all six services with a bootstrap change | all pass: sentiment-service 16, video-service 12, analytics-service 8, api-gateway 57, chat-service 25, recommendation-service 5. chat-service first failed one Embedded Kafka test (`ChatKafkaProducerIntegrationTest.sameStreamerKey_alwaysRoutesToSamePartition…`, "No records found for topic") while three suites ran concurrently; rerun alone it passed 25/25, so it is a load-sensitive test, not a regression |

## Manual checks for the reviewer

1. From a fresh clone of the branch: `make up` (or `tools/start-stack.ps1 -TwitchEnv -Channels redbull-testing`). Every service should reach healthy. This exercises the `configtree` path end to end, which unit tests cannot.
2. `docker compose exec sentiment-service sh -c 'ls /run/secrets'` should list `POSTGRES_PASSWORD`; `docker compose logs sentiment-service | grep -i "Could not resolve placeholder"` should be empty.
3. `make replay-smoke` passes.
4. Change `secrets/POSTGRES_PASSWORD`, run `make nuke && make up-fast`, and confirm the three Postgres-backed services still come up (proves the value is really read from the file).
5. Optional auth check: write a 32+ byte value to `secrets/STREAMSENSE_GATEWAY_AUTH_HMAC_SECRET`, run `STREAMSENSE_GATEWAY_AUTH_ENABLED=true docker compose up -d --force-recreate api-gateway`, and confirm an unauthenticated GraphQL call returns 401.
6. On kind: `kubectl apply -k k8s` after `make secrets`; `kubectl -n streamsense get secret | grep streamsense-secrets` shows one hash-suffixed Secret with 5 keys. Change a value in `k8s/secrets/streamsense.env`, apply again, and confirm the pods that reference it roll.

## Follow-ups (not in this branch)

- Branch 04 replaces the hand-mirrored config-repo ConfigMap with `configMapGenerator`, which removes the need to edit two files for one config change.
- Once a real secret store exists, swap `secretGenerator` for External Secrets; every manifest already reads through `secretKeyRef`, so only the generator changes.
