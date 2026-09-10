# cloud/05-domain-tls

The first phase-two item of `docs/planning/cloud-hosting.md`: the console served at the owner's domain, `streamsense.dev`, over HTTPS. Based on `main` after branches 01 to 04 merged (#45) and the first deploy ran (#47).

## What was missing

The overlay published the console's nginx straight onto port 80 of the VM's address. That works for an address but not for the domain: `.dev` is on the HSTS preload list every browser ships, so a browser refuses plain HTTP for any `.dev` name before sending a request. Serving the name means serving HTTPS, which needs a certificate and something to terminate TLS in front of the console.

## What changed

- **Caddy as the edge** (`docker-compose.prod.yml`): a `caddy` service, image pinned by tag and digest, publishes 80 and 443 (TCP, plus 443 UDP for HTTP/3) and proxies everything to `frontend:8080`; the frontend no longer publishes a host port. Certificates and Caddy's state live in two named volumes, so a stop, start, or redeploy never asks Let's Encrypt again. All capabilities are dropped except `NET_BIND_SERVICE`. The healthcheck probes a loopback-only health site on port 8081 inside the container (`http://127.0.0.1:8081/healthz` in the Caddyfile), added after the first HTTPS deploy: the original probe of port 80 with a bare `127.0.0.1` host never matched the domain site, so Caddy stayed `unhealthy` in Compose while serving `https://streamsense.dev` fine, and the deploy script waited out its fifteen minutes. Memory limit 256 MB. The gateway's trusted proxy hops go from 1 to 2 (Caddy sets `X-Forwarded-For`, nginx appends its own).
- **`tools/deploy/caddy/Caddyfile`**: one site block whose address is `{$STREAMSENSE_DOMAIN}`. With the domain set, Caddy serves it on 443 with automatic HTTPS (HTTP-01 challenge, HTTP redirected) and the `.dev` explanation in the comments; without one, the overlay passes `:80` as the variable and the behaviour is the plain HTTP of before. The substitution lives in the Compose file rather than as a Caddyfile default (`{$VAR:default}`) because `caddy validate` showed Caddy treats an empty variable as set: the block lost its address and was parsed as a second global block. `reverse_proxy` to the frontend with no response header timeout, because GraphQL subscriptions are long-lived WebSockets. The admin API is off.
- **Firewall** (`terraform/main.tf`): the HTTP rule now allows TCP 80 and 443 and UDP 443, still from `allowed_http_cidrs`. Applying it is a `terraform apply` that changes one rule in place.
- **`tools/deploy/deploy.sh`**: `STREAMSENSE_DOMAIN` is resolved like the tag (shell, else the env file, else empty) and exported for the overlay. With a domain, `deploy` first checks that the name resolves to this VM's address and stops with a clear message if not, because the certificate cannot be issued otherwise. `verify` then talks to Caddy on the VM with the public name (`curl --resolve`), waits up to five minutes for a valid certificate on `https://<domain>/healthz`, checks that plain HTTP redirects, and runs the gateway checks over HTTPS; the public-listener check allows 443. Printed URLs and the sharing instructions use the domain when set.
- **`tools/deploy/twitch.env.example`**: a `STREAMSENSE_DOMAIN` line, commented.
- **`docs/hosting.md`**: a Domain and HTTPS section (the Vercel DNS steps, the env line, what the first deploy does, how to go back), a certificate entry under troubleshooting, and the listener list updated to 22, 80, and 443. **CLAUDE.md** updated. The plan's phase-two bullet points here.

## Deliberately left alone

- **`www.streamsense.dev` is not served.** One name keeps the certificate and the DNS simple; adding `www` is a second `A` record and a second name in the Caddyfile's site address.
- **No Cloudflare or other proxy in front.** Caddy alone is enough for a demo, and the VM's address is already public through the A record; a proxy that hides it is a later choice.
- **No security headers in Caddy.** The console's nginx already sets the CSP and the other headers; Caddy passes them through. HSTS is implied by the `.dev` preload, so no header is needed for the browser to enforce it.
- **The IP-only mode stays** (no `STREAMSENSE_DOMAIN`), so a VM without a name still works and the runbook's fallback holds.
- **Terraform does not know the domain.** DNS lives at Vercel, outside GCP; managing it from Terraform would mean a Vercel provider and token for one record.

## Verification

| Check | Command | Result |
|---|---|---|
| Overlay renders | `docker compose -f docker-compose.yml -f docker-compose.prod.yml config` with and without `STREAMSENSE_DOMAIN` (Compose 5.5) | both render; caddy publishes 80, 443, and 443/udp and is the only service on a public port, the frontend publishes nothing, the gateway has two proxy hops, caddy's environment carries `:80` without a domain and the name with one |
| Caddyfile | `caddy validate` and `caddy adapt` with the 2.11.4 binary, `STREAMSENSE_DOMAIN=:80` and `=streamsense.dev` | both valid; adapted config listens on `:80` with no host match, and on `:443` matching `streamsense.dev`; admin disabled. The first version used `{$STREAMSENSE_DOMAIN::80}` and failed with an empty variable, see above |
| Script | `bash -n`, `shellcheck -S style tools/deploy/deploy.sh` (0.11.0) | clean |
| Terraform | `terraform fmt -check`, `validate`, `tflint` (0.64.0, google 0.39.0), Trivy misconfiguration gate (0.74.0) | all clean; the plan against the live project changes the one firewall rule in place |
| Cloud | A record at Vercel (2026-09-10; Vercel's form rejects a trailing dot on the address), `STREAMSENSE_DOMAIN=streamsense.dev` in the env file, `sudo streamsense-deploy` | Caddy obtained the certificate on the first start and the owner opened `https://streamsense.dev/` in a browser successfully. The script itself never reached its edge checks: the Caddy healthcheck stayed `unhealthy` (see above) until the VM was stopped. Fixed in the follow-up commit on this branch; the edge checks over HTTPS are re-run on the next deploy |

## What to check by hand

1. `terraform apply` (one firewall rule changes in place).
2. Add the `A` record for `@` at Vercel pointing at 35.193.171.122; confirm with `nslookup streamsense.dev`.
3. Start the VM, add `STREAMSENSE_DOMAIN=streamsense.dev` to `/etc/streamsense/twitch.env`, run `sudo streamsense-deploy`.
4. Open `https://streamsense.dev/` in a browser: the padlock is valid, the console loads, and after setting the token the live panels update over `wss`.
