# Local secrets for Docker Compose

Docker Compose mounts each file in this directory into the containers that need it at `/run/secrets/<NAME>`. The real files are git-ignored; only the `*.example` files and this README are committed. The examples hold placeholders, not usable values: they document which files exist.

Create the real files once:

```bash
make secrets
```

Every missing file gets a fresh random value (hex from `openssl rand`), so a clone never runs on credentials that are known outside the machine. The `secrets/` and `k8s/secrets/` directories are made `0700`, so other local users cannot reach the files; the files themselves are `0644` because Compose bind-mounts them into the containers, which read them as their own non-root users (the Java images run as uid 10001, Grafana as 472), and a `0600` file owned by you is unreadable there. Existing files are left alone. The target refuses to run without `openssl`, never leaves a partially written file behind, and stops with instructions if a Postgres or MinIO file is missing while the matching Compose volume already exists (that volume was initialised with an older credential, so a new random one would lock the services out). `make up`, `make up-fast`, `make smoke-e2e`, and `tools/start-stack.ps1` run this step automatically; the PowerShell script generates values the same way. To pick a value yourself, write it into the file before the first start.

| File | Used by | Notes |
|---|---|---|
| `POSTGRES_PASSWORD` | postgres, sentiment-service, video-service, analytics-service | Spring reads it through `spring.config.import: optional:configtree:/run/secrets/`, so `${POSTGRES_PASSWORD}` in `config-server/config-repo/*.yml` resolves from this file or from an environment variable of the same name. |
| `STREAMSENSE_FRAME_STORAGE_ACCESS_KEY` | minio (root user), ml-engine, video-capture-service | The Python services read `<NAME>_FILE` first, then `<NAME>`. MinIO requires 3 to 20 characters; the generator writes 16. |
| `STREAMSENSE_FRAME_STORAGE_SECRET_KEY` | minio (root password), ml-engine, video-capture-service | Same as above. MinIO requires 8 to 40 characters; the generator writes 32. |
| `GRAFANA_ADMIN_PASSWORD` | grafana | Wired as `GF_SECURITY_ADMIN_PASSWORD__FILE`. The admin username stays `admin`. |
| `STREAMSENSE_GATEWAY_AUTH_HMAC_SECRET` | api-gateway | Only read when `STREAMSENSE_GATEWAY_AUTH_ENABLED=true`, which requires at least 32 bytes; the generator writes 64. Pass the same value to `tools/mint-jwt.py` when minting tokens. |

Values are read verbatim and trailing whitespace is trimmed, so a file may end with or without a newline.

To rotate a value, delete its file and rerun `make secrets` (or write the new value). Postgres and MinIO persist the credentials they were first started with, so rotating those also needs `make nuke` (or `docker compose down -v`). Upgrading a checkout that already has data volumes: write the volume's existing credential into the file (the historical development default was `streamsense` for all three) to keep the data, or `make nuke` to start clean; `make secrets` and `start-stack.ps1` detect this case and stop with the same instructions.

Kubernetes uses a separate file, `k8s/secrets/streamsense.env`, because kustomize can only read files below its own directory. `make secrets` writes it with the same values; see `k8s/secrets/streamsense.env.example`.
