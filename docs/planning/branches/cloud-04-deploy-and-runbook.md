# cloud/04-deploy-and-runbook

Branch 04 of `docs/planning/cloud-hosting.md`: the script that puts the stack on the VM and checks it, the Twitch env template it reads, and the runbook that ties the four branches together. Stacked on `cloud/03-terraform`.

## What was missing

Branches 01 to 03 produced images, an overlay, and a VM, but no repeatable way to get from a fresh VM to a verified, shareable console, and no document a person could follow to run the demo without reading the plan.

## What changed

- **`tools/deploy/deploy.sh`** (new, bash, shellcheck-clean at `-S style`, committed executable; the startup script from branch 03 links it as `/usr/local/bin/streamsense-deploy`). Subcommands `deploy` (default), `verify`, `token`, `status`. `deploy` runs as root on the VM. It first resolves the versions: `STREAMSENSE_IMAGE_TAG` from the shell, else from the env file's line, else `main` (exported only after the file was read, because Compose gives the shell precedence over `--env-file`), and `STREAMSENSE_REPO_REF` defaulting to the tag, so the `config-server/config-repo` the config-server bind-mounts from the checkout is the one the images were built with. It then fetches and checks out that ref in `/opt/streamsense` (pulls only when it is a branch), runs `make secrets` (existing values kept), pulls the images, starts the stack with `docker compose --env-file /etc/streamsense/twitch.env -f docker-compose.yml -f docker-compose.prod.yml up -d --no-build --remove-orphans`, and waits up to fifteen minutes for every service the two files define to be healthy (services without a healthcheck must be running; `kafka-topics-init` must have exited 0; a service with no container is reported as missing), printing the last log lines of the laggards on timeout. `verify` then goes through port 80: the console's `/healthz`, a GraphQL health query without a token (must be 401), the same with a freshly minted ten-minute token (must answer `ok`), and a check with `ss` that nothing but 22 and 80 listens on a public interface. Finally it prints the URL, a 30-day token minted with `tools/mint-jwt.py` (the HMAC secret passed through the environment, not the command line), and the `localStorage.setItem` line a viewer runs once. The external address comes from the GCE metadata server, with a fallback to the first local address so the script also works off GCE.
- **`tools/deploy/twitch.env.example`**: the variables a live capture needs (chat username and OAuth token, video OAuth token, channels, the three capture toggles), with the optional tuning and `STREAMSENSE_IMAGE_TAG` commented. No client id: the Compose file passes none through and the stack uses Twitch's public web client id (`TWITCH_GQL_CLIENT_ID` in `chat-service.yml` has that default), so a line for it would be silently ignored. The script refuses to run without the real file and forces it to `0600`.
- **`docs/hosting.md`**: one-time setup (making the GHCR packages public, project, gcloud, Terraform, apply, the Twitch env created under `umask 077` and its staging copy removed after `install`), deploy, sharing (including the local-storage step), stop and start between demos, updating to `main` or a SHA and why the checkout follows the tag, admin UIs over an SSH tunnel, logs and troubleshooting (unhealthy or missing containers, Twitch refusing cloud addresses, memory, disk), teardown, and cost.
- **CLAUDE.md** and **README.md** point at the runbook.

## Deliberately left alone

- **`tools/smoke/compose_smoke.py` is not run on the VM.** It probes the service ports on `localhost` (8080, 8081, 8083, 9090, 3001, 9411), which the overlay no longer publishes, and sends no bearer token, so it cannot pass with auth on. The script's own edge checks cover what the demo needs; teaching the smoke script a base URL through the console and a token is a small, separate change if a fuller API smoke on the VM is wanted.
- **No token entry in the console.** The console reads the token from local storage only (`frontend/src/lib/auth-token.ts`), so viewers paste one line into the browser console. Accepting `#token=` in the URL and storing it would make sharing a single link; that is a frontend change and goes in a `feat/` branch.
- **No systemd unit for the deploy.** `restart: unless-stopped` brings the stack back after a stop and start; the script is for changes, not for boot.
- **Memory limits are unchanged from branch 02.** The plan expected a tuning pass after the first live session; that pass needs the numbers from the VM (below).

## Verification

| Check | Command | Result |
|---|---|---|
| Script syntax and lint | `bash -n tools/deploy/deploy.sh`; `shellcheck -S style tools/deploy/deploy.sh` (0.11.0) | clean |
| Health-wait parser | the embedded Python run against sample `docker compose ps --format json` lines (healthy, running without healthcheck, exited 0 init, unhealthy, plus an expected service with no row) | reports exactly the unhealthy entries and the missing one |
| Version resolution | `resolve_versions` with a tag in the env file, with a shell value and an explicit ref, and with neither | file value used with the ref following it; shell value wins and the explicit ref is kept; `main` for both |
| Cloud | `terraform apply`, Twitch env copied, `sudo deploy.sh`, `deploy.sh verify`, a live capture session, `docker stats` | not run from the agent's environment (no GCP credentials); the steps are in `docs/hosting.md` and this table is to be completed by the owner on the first deploy |

## What to check by hand

1. Follow `docs/hosting.md` one-time setup through `terraform apply`, then copy the Twitch env and run `sudo streamsense-deploy`. Expect the health wait to take several minutes on the first run and the four edge checks to pass.
2. Open the URL in a browser, set the token as printed, and confirm the console shows the health pills green and live chat for the configured channel.
3. Watch `docker stats` for ten minutes of live capture with transcripts on and note the peak of ml-engine, video-capture-service, and the JVMs against the limits in `docker-compose.prod.yml`; if one sits above 80 percent of its limit, raise it in a branch.
4. Stop and start the VM with the Terraform outputs; `sudo streamsense-deploy verify` should pass without a redeploy.
