# cloud/02-compose-prod

Branch 02 of `docs/planning/cloud-hosting.md`: a Compose overlay that turns the developer stack into the hosted demo. Stacked on `cloud/01-image-publish`.

## What was missing

`docker-compose.yml` is written for a developer machine: every service builds from source, every port (Postgres, Redis, Kafka, MinIO, the eight service ports, the admin UIs) is published on all interfaces, nothing has a memory limit (each JVM sizes its heap from the whole machine), nothing restarts after a reboot, logs grow without bound, and gateway auth is off with GraphiQL on. On a VM with a public address that is an open database and an unauthenticated API.

## What changed

- **`docker-compose.prod.yml`** (new), layered over the base file with a second `-f`. Per service it sets the GHCR image at `${STREAMSENSE_IMAGE_TAG:-main}` (the images branch 01 publishes), resets the host ports (`!reset []`) except the console on `80:8080` and the admin UIs on loopback (`127.0.0.1:3001` Grafana, `9090` Prometheus, `9411` Zipkin, `8088` Kafka UI, `9001` MinIO console), copies the memory limit from the matching Kubernetes container (about 25 GiB summed; the JVMs read the cgroup through `-XX:MaxRAMPercentage=75`), and applies `restart: unless-stopped` with a rotated `json-file` log (three 50 MB files) through one YAML anchor. The gateway gets `AUTH_ENABLED=true`, `GRAPHIQL_ENABLED=false`, and `TRUSTED_PROXY_HOPS=1` (the console's nginx is the one hop that sets `X-Forwarded-For`). `kafka-topics-init` keeps `restart: "no"` and gets only the log rotation.
- **Twitch settings** are not in the overlay. The base file already interpolates `${TWITCH_*}` and `${STREAMSENSE_TWITCH_*}` from the Compose environment, and a service-level `env_file` cannot win over those `environment` entries, so the deploy passes `docker compose --env-file /etc/streamsense/twitch.env`, which is what interpolation reads. The overlay's header says so; branch 04 ships the example file and the script.
- **CI** renders the pair (`docker compose -f docker-compose.yml -f docker-compose.prod.yml config -q`) in the smoke job next to the existing base render, and the overlay is in the `smoke` path filter.
- **CLAUDE.md** describes the overlay in the Compose section.

## Deliberately left alone

- No local boot of the overlay on the developer machine: the images it pulls are not public until the owner flips the package visibility after branch 01's first run, and the whole stack under production limits is more than a laptop should carry. The VM in branch 04 is the real proof.
- ml-engine model preload stays off. Preloading the sentiment and relevance models at start would push the first boot past the healthcheck window and hold every dependent service back; the first request warms them instead.
- Memory limits are the Kubernetes numbers. Branch 04 records what each container settles at under live capture and adjusts once.
- MinIO's API port is not published at all; the console serves captured frames through the gateway.

## Verification

| Check | Command | Result |
|---|---|---|
| Overlay renders | `docker compose -f docker-compose.yml -f docker-compose.prod.yml config` (Compose v5.1.1, Windows binary, daemon not needed) | exit 0; rendered output checked service by service: eleven GHCR images at `:main`, only `0.0.0.0:80->8080` public, five loopback admin ports, limits and `unless-stopped` on every long-running service, the three gateway variables set |
| Workflow lint | `actionlint .github/workflows/ci.yml` | clean |
| CI smoke job | pull request run | records the new render step |

## What to check by hand

- `docker compose -f docker-compose.yml -f docker-compose.prod.yml config | grep -A3 ports:` shows only the console and the loopback UIs.
- After branch 01's images are public: `STREAMSENSE_IMAGE_TAG=<sha> docker compose -f docker-compose.yml -f docker-compose.prod.yml pull` fetches all eleven without a login.
