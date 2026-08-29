# Security policy

## Reporting a vulnerability

Email the maintainer at ujjawalprasad111@gmail.com with "StreamSense security" in the subject. Include the affected service or path, a reproduction, and the impact you believe it has. Please do not open a public issue for a vulnerability.

You will get an acknowledgement within 72 hours and a fix or mitigation plan within 14 days for anything that lets an unauthenticated caller read or change data, reach an internal service, or exhaust a shared resource.

## Scope

- The eight Spring Boot services, the two Python services, and the React console in this repository.
- The Docker Compose and Kubernetes deployment files under `docker-compose.yml`, `k8s/`, and `kustomization.yaml`.

Third-party images and libraries are updated through Renovate and scanned with Trivy in CI; report a vulnerability in one of them upstream and open an issue here only if the update is blocked.

## What is already in place

- Secrets are never committed; see `secrets/README.md` and the `secretGenerator` in `k8s/kustomization.yaml`.
- The gateway validates JWTs when `STREAMSENSE_GATEWAY_AUTH_ENABLED=true` and rate-limits ingest routes through Redis.
- Every container runs as a non-root user with all capabilities dropped; the console's nginx sends a Content-Security-Policy.
- CI pins GitHub Actions by commit, images by digest, and fails on Trivy findings of HIGH or CRITICAL severity with a fix available.
