# hardening/12a-nginx

Priority 11 from `docs/planning/production-hardening.md`: the frontend container runs unprivileged, serves the console the way a CDN would, sends the security headers a browser expects, and exposes only the ml-engine route the console uses. Stacked on `hardening/11d-frontend-tests` (the Dockerfile builds the same tree).

## What was wrong

- The serve stage was the stock `nginx` image running as root on port 80, with no gzip, no caching policy (hashed bundles were revalidated on every load, the HTML shell could be cached stale), no security headers, and a `/ml/` proxy that exposed every ml-engine endpoint (transcription, sponsor detection, health, info) to anyone who could reach the console.
- The build stage copied the whole tree before `npm ci`, so any source change reinstalled `node_modules`.
- Kubernetes had no frontend at all: the ingress exposed the gateway, Grafana, and Zipkin, and the kind guide never built or loaded a console image.

## What changed

- **`frontend/Dockerfile`**: `npm ci` runs from `package.json` + `package-lock.json` before the sources are copied (cached layer); the serve stage is `nginxinc/nginx-unprivileged:1.31.5-alpine`, digest-pinned like every other image, which runs as uid 101, listens on 8080, and keeps pid and cache under `/tmp`. A `HEALTHCHECK` hits `/healthz`.
- **`frontend/nginx.conf`**: `server_tokens off`; gzip for text, JS, JSON, CSS, SVG; `Cache-Control: public, max-age=31536000, immutable` for `/assets/` (Vite content-hashed names) and `no-cache` for everything else so a deploy is picked up on the next load; `/healthz` answered locally; SPA fallback to `index.html`; `/graphql` (WebSocket upgrade, hour-long read timeout) and `/api/` (10 MB body for frame uploads) proxied to the gateway with `X-Forwarded-Proto`; `POST /ml/segment` is the only ml-engine route (`limit_except POST`, 1 MB body, 120 s read timeout for a cold model); every other `/ml/` path returns 404.
- **`frontend/streamsense-headers.conf`**: `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`, `Permissions-Policy` (camera, microphone, geolocation, payment off), and a Content-Security-Policy that matches what the built app needs: same-origin scripts and API calls (`connect-src 'self' ws: wss:` for subscriptions), inline style attributes (React), `data:`/`blob:` images, `frame-src https://player.twitch.tv` for the embedded player, `frame-ancestors 'none'`, `object-src 'none'`. The file is included at server level and again in each location that adds its own headers, because nginx's `add_header` does not inherit.
- **Compose**: publishes `3000:8080` and health-checks `/healthz`.
- **Rate limiting still sees the real client on Kubernetes.** Through `streamsense.local` a request passes ingress-nginx (which overwrites `X-Forwarded-For` with the client address) and then the console's nginx (which appends the ingress address), so the gateway's `STREAMSENSE_GATEWAY_TRUSTED_PROXY_HOPS` is now `2` on Kubernetes; `XForwardedRemoteAddressResolver.maxTrustedIndex` clamps to the first entry when a direct `gateway.streamsense.local` request carries only one, so both paths key the limiter on the client. Compose keeps `0` (the gateway port is published directly, so a forwarded header there is client-controlled), which means requests through the Compose console share one bucket; that trade-off predates this branch and is noted here rather than changed.
- **Kubernetes**: `k8s/apps/frontend.yaml` (Deployment: `runAsNonRoot` uid 101, `readOnlyRootFilesystem` with `emptyDir` scratch for `/tmp` and `/var/cache/nginx`, all capabilities dropped, seccomp `RuntimeDefault`, requests and a memory limit, readiness and liveness on `/healthz`; Service on 8080) added to the kustomization, and a `streamsense.local` ingress in front of it with the same long proxy timeouts as the gateway ingress. The kind guide builds and loads `streamsense/frontend:sprint9` and lists the new host.
- **`/ml/segment` goes through the gateway, not straight to ml-engine.** Review found that the direct proxy bypassed the gateway's auth and rate-limit filters while `/ml/segment` accepts `file://` references, so an unauthenticated request could have read an unbounded local path on the ML pod. The gateway gains an `ml-engine-segment` route (`POST /ml/segment` to `ML_ENGINE_URL`, 180 s response timeout for the SAM cold start), `/ml/**` joins the auth filter's protected paths, and a `ml-segment` rate-limit rule allows 30 requests per minute per client; nginx and the Vite dev proxy forward `/ml/segment` to the gateway, and the frontend drops its separate ML origin setting. Independently, ml-engine now reads only regular files and bounds every frame read (file and S3) by `FrameStorageSettings.max_bytes` (`STREAMSENSE_FRAME_STORAGE_MAX_BYTES`, default 32 MiB, validated at start-up as a positive number no larger than 1 GiB and passed into `FrameStore`), with tests. ml-engine is a required `streamsense.services.ml-engine.base-url` like the six other downstream services, so a deployment that cannot resolve it fails at start-up rather than on the first segmentation call.
- **A configured API origin is allowed by the CSP.** `VITE_API_BASE_URL` is a Docker build argument (Compose passes it through) that both bakes the origin into the bundle and replaces `__API_ORIGIN__` in `connect-src`, so the documented separate-origin build is not blocked by the console's own policy; empty keeps same-origin.
- **Missing hashed assets are not cached.** During a rolling update old and new console pods serve together, and a 404 for a new bundle from an old pod must not carry the year-long immutable policy: the `/assets/` header no longer uses `always`, and a `@asset_missing` location answers 404 with `Cache-Control: no-store`.
- **CLAUDE.md** describes the container's behaviour and the `/ml/` restriction.

## Deliberately left alone

- No TLS termination in nginx: on Compose the console is a local dev surface and on Kubernetes the ingress controller terminates TLS. `X-Forwarded-Proto` is forwarded so the gateway sees the scheme.
- `style-src` keeps `'unsafe-inline'` because React inline `style={}` attributes need it; moving those styles into classes would allow a stricter policy later.
- The `Server: nginx` header stays (only the version is hidden); stripping it needs the headers-more module, which the stock image does not ship.
- No `Strict-Transport-Security`: it belongs on the TLS-terminating hop.

## Verification

| Check | Command | Result |
|---|---|---|
| nginx config syntax | `nginx -t` in the unprivileged image with stub hosts for the upstreams | syntax ok, test successful |
| Image builds | `docker build frontend/` | OK |
| Runs unprivileged | `docker exec … id -u`, pid 1 | uid 101, pid 1 is `nginx` |
| Health | `GET /healthz` | 200 `text/plain` |
| Headers on the shell | `curl -D - /` | `Cache-Control: no-cache`, CSP, `X-Frame-Options: DENY`, `nosniff`, referrer and permissions policies present |
| Hashed assets | `curl -H 'Accept-Encoding: gzip' /assets/index-*.js` | `Cache-Control: public, max-age=31536000, immutable`, `Content-Encoding: gzip`, `Vary: Accept-Encoding` |
| SPA fallback | `GET /some/route` | 200 (`index.html`) |
| Gateway proxy | `GET /api/chat/twitch/status` against a stub on 8080 | answered by the stub |
| ml-engine restriction | `GET /ml/live`, `GET /ml/segment`, `POST /ml/segment` against a stub on 8000 | 404, 403, forwarded to the stub (which answered 501 because the stub has no POST handler, proving the request reached it) |
| Compose renders | `docker compose config -q` | OK |
| Kubernetes renders and validates | `kubectl kustomize .`, `kubeconform -strict`, `kube-linter lint k8s/apps/frontend.yaml` | 54 resources valid; no lint errors |

## Manual checks for the reviewer

1. `make up`, open `http://localhost:3000`: the console loads, the Twitch player embeds (CSP `frame-src`), subscriptions stream (CSP `connect-src` allows `ws:`), and the browser console shows no CSP violations.
2. DevTools Network: `index.html` is `no-cache`; `assets/*.js` are `immutable` and served gzipped.
3. `curl -i http://localhost:3000/ml/health` is a 404; `curl -i http://localhost:3000/ml/segment` (GET) is 403; the segmentation panel still works (POST).
4. On kind: build and load `streamsense/frontend:sprint9`, `kubectl apply -k .`, add `streamsense.local` to `/etc/hosts`, open it; `kubectl get pod -l app=frontend -o jsonpath='{.items[0].spec.containers[0].securityContext}'` shows the read-only root filesystem and dropped capabilities.

## Follow-ups

- Replace inline style attributes with classes and drop `'unsafe-inline'` from `style-src`.
- Add a CSP report endpoint once there is somewhere to send reports.
