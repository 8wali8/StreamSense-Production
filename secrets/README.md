# Local secrets for Docker Compose

Docker Compose mounts each file in this directory into the containers that need it at `/run/secrets/<NAME>`. The real files are git-ignored; only the `*.example` files and this README are committed.

Create the real files from the examples once:

```bash
make secrets
```

or, without make:

```bash
for f in secrets/*.example; do cp -n "$f" "${f%.example}"; done
```

`tools/start-stack.ps1` does the same copy automatically when a file is missing.

| File | Used by | Notes |
|---|---|---|
| `POSTGRES_PASSWORD` | postgres, sentiment-service, video-service, analytics-service | Spring reads it through `spring.config.import: optional:configtree:/run/secrets/`, so `${POSTGRES_PASSWORD}` in `config-server/config-repo/*.yml` resolves from this file or from an environment variable of the same name. |
| `STREAMSENSE_FRAME_STORAGE_ACCESS_KEY` | minio (root user), ml-engine, video-capture-service | The Python services read `<NAME>_FILE` first, then `<NAME>`. |
| `STREAMSENSE_FRAME_STORAGE_SECRET_KEY` | minio (root password), ml-engine, video-capture-service | Same as above. |
| `GRAFANA_ADMIN_PASSWORD` | grafana | Wired as `GF_SECURITY_ADMIN_PASSWORD__FILE`. The admin username stays `admin`. |
| `STREAMSENSE_GATEWAY_AUTH_HMAC_SECRET` | api-gateway | Leave empty while gateway auth is disabled. Set a value of at least 32 bytes before enabling `STREAMSENSE_GATEWAY_AUTH_ENABLED=true`; the gateway refuses to start otherwise. |

Values are read verbatim and trailing whitespace is trimmed, so a file may end with or without a newline.

The example values are the historical development defaults. They are fine for a throwaway local stack and must be changed for anything reachable by other people. Changing `POSTGRES_PASSWORD` or the frame storage keys after the first start requires `make nuke` (or `docker compose down -v`), because Postgres and MinIO persist the credentials they were initialised with.

Kubernetes uses a separate file, `k8s/secrets/streamsense.env`, because kustomize can only read files below its own directory. See `k8s/secrets/streamsense.env.example`.
