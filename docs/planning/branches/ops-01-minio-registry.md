# ops/01-minio-registry

A hotfix found on 2026-09-11 while redeploying the demo: `streamsense-deploy` stopped at `compose pull` with "pull access denied for minio/minio, repository does not exist or may require 'docker login'". The `minio/minio` repository on Docker Hub now answers 404 (the tag too); MinIO publishes the same images on quay.io, where the pinned tag resolves to the very same digest (`sha256:a1ea29fa…`, confirmed with `docker buildx imagetools inspect`).

## What changed

- `docker-compose.yml` and `k8s/platform/minio.yaml`: the image reference is `quay.io/minio/minio:RELEASE.2025-04-22T22-12-26Z@sha256:a1ea29fa…`. Same tag, same digest, different registry; nothing about the running container changes.

## Deliberately left alone

- **No version bump.** The digest is identical, so the image bytes are the ones already verified in `docs/planning/branches/02b-image-pinning.md`; moving registries and upgrading in one step would muddle the diff.
- **Renovate** keeps working: its digest pinning understands quay.io.

## Verification

| Check | Command | Result |
|---|---|---|
| Registry | `curl https://hub.docker.com/v2/repositories/minio/minio/` (404); `docker buildx imagetools inspect quay.io/minio/minio:RELEASE.2025-04-22T22-12-26Z` | Docker Hub repository gone; quay.io digest `a1ea29fa…`, identical to the pin |
| Compose | `docker compose -f docker-compose.yml -f docker-compose.prod.yml config -q` | renders |
| Live | the VM still runs the image from its local cache; the next `sudo streamsense-deploy` pulls from quay.io | to confirm on the next deploy |

## What to check by hand

1. `sudo streamsense-deploy` completes its pull step again.
