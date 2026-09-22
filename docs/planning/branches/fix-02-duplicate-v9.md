# fix/02-duplicate-v9

`imports/03-chat-log` and `logo/01-deal-logo` were built side by side from the same `main`, and each added analytics-service's next Flyway migration as `V9`. Both merged; on `main` at `9d50ee3` Flyway finds two migrations with version 9 and refuses to start, so every analytics-service Spring context fails (CI on `main` red since that merge) and a fresh deploy would leave analytics-service down. Nothing deployed has applied either file: the VM last deployed `e120ea3`, before both.

## What changed

- `V9__deal_logos.sql` is `V10__deal_logos.sql`. The logo branch merged last, so its migration takes the next number; the imports branch's `V9__vod_chat_logs.sql` keeps its own. The file's content is unchanged.
- The logo branch note and the detection plan name the file by its new number.

## Verification

| Check | Command | Result |
|---|---|---|
| analytics-service | `mvn -pl analytics-service -Dmaven.gitcommitid.skip=true verify` in `maven:3.9-eclipse-temurin-21` | build success; 70 tests, 0 failures, 0 errors (on `main` before the fix: 34 errors, every Spring context) |

## What to check by hand

1. CI on `main` green after the merge.
2. After the deploy, `streamsense-deploy status`: analytics-service healthy, and its log shows Flyway at version 10.
