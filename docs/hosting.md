# Hosting runbook: the demo on one GCP VM

How to create, run, share, pause, update, and remove the hosted demo. The decisions behind it are in `docs/planning/cloud-hosting.md`; the pieces are `terraform/` (the VM), `docker-compose.prod.yml` (what runs on it), `tools/deploy/deploy.sh` (how it gets there), and the `publish-images` CI job (where the images come from).

## What you get

One `e2-standard-8` (8 vCPU, 32 GB) in its own VPC with a static address. Caddy on ports 80 and 443 serves the console to whoever the firewall admits, over HTTPS at your domain once one is configured (see Domain and HTTPS) and over plain HTTP at the address until then; port 22 is open to your address only, through OS Login. Every service runs from the images CI published for a commit, with the memory limits, restart policy, and gateway auth from the overlay. Grafana, Prometheus, Zipkin, Kafka UI, and the MinIO console listen on the VM's loopback and are reached over an SSH tunnel.

Cost while running is about $0.27 per hour for the VM plus a few dollars a month for the 100 GB disk and the reserved address; a stopped VM costs only the disk and the address. Check the current prices in the console before relying on these.

## One-time setup

The eleven packages under `ghcr.io/8wali8/streamsense/` are public: they are pushed with the workflow token from a public repository, so they inherit its visibility, and the VM pulls them without credentials. If the repository is ever made private, the packages follow, and the deploy would then need a `docker login ghcr.io` on the VM with a read-only token (not scripted).

On your machine:

1. A GCP project with billing enabled. Note its id.
2. Install the [gcloud CLI](https://cloud.google.com/sdk/docs/install) and [Terraform](https://developer.hashicorp.com/terraform/install) (1.16 or later).
3. Log in, twice: `gcloud auth login` for the CLI, `gcloud auth application-default login` for Terraform.
4. In `terraform/`: copy `terraform.tfvars.example` to `terraform.tfvars`; set `project_id` and your public address as a `/32` in `allowed_ssh_cidrs` (`curl -4 ifconfig.me`). Leave `allowed_http_cidrs` alone to let anyone with the address and a token open the console, or list your viewers' addresses.
5. `terraform init`, `terraform plan` (ten resources), `terraform apply`. The first apply also enables the Compute Engine API, which can take a minute.
6. `terraform output`: the address, the console URL, and the `gcloud` commands to SSH, stop, and start. The VM's first boot installs Docker, clones the repository, and links the deploy script as `streamsense-deploy`; give it two or three minutes.

Then the Twitch settings, once. The file holds OAuth tokens, so it is created readable by you alone, copied to a staging path only root can read once installed, and the staging copy is removed:

```bash
(umask 077 && cp tools/deploy/twitch.env.example twitch.env)   # fill in the credentials and channels; never commit it
gcloud compute scp twitch.env streamsense-demo:/tmp/twitch.env --zone us-central1-a
gcloud compute ssh streamsense-demo --zone us-central1-a -- \
  'sudo install -m 0600 -o root -g root /tmp/twitch.env /etc/streamsense/twitch.env && rm -f /tmp/twitch.env'
rm twitch.env
```

Two credentials are secret files rather than env lines: the Twitch application client id and secret that analytics-service uses for Helix polling (`STREAMSENSE_TWITCH_HELIX_ENABLED`) and for listing a channel's recordings to import. `make secrets`, which the deploy runs, creates them empty, and with the toggle off nothing reads them. To turn Helix on, write the values on the VM once and redeploy:

```bash
gcloud compute ssh streamsense-demo --zone us-central1-a
sudo sh -c 'umask 077; printf "%s" "<client id>" > /opt/streamsense/secrets/TWITCH_CLIENT_ID; printf "%s" "<client secret>" > /opt/streamsense/secrets/TWITCH_CLIENT_SECRET; chmod 644 /opt/streamsense/secrets/TWITCH_CLIENT_*'
```

(`644` because the container reads them as its own non-root user; the directory itself is `0700`.) Then set `STREAMSENSE_TWITCH_HELIX_ENABLED=true` in `/etc/streamsense/twitch.env` and run `sudo streamsense-deploy`.

## Deploy

On the VM (`terraform output ssh_command` prints the exact command):

```bash
sudo streamsense-deploy
```

`streamsense-deploy` is `/opt/streamsense/tools/deploy/deploy.sh`, linked into `/usr/local/bin` by the startup script.

The script checks out the commit it will run (see Updating), refreshes the local secret files (`make secrets`, random values kept between runs), pulls the images at that tag, starts the stack with the overlay, and waits for every container the two Compose files define to report healthy. The first run pulls about 4 GB of images and, once the services are up, ml-engine downloads its models on the first request for each backend (2 to 3 GB in total), so allow ten minutes before the console is fully useful. Later runs are a minute or two.

It then verifies through Caddy that the console answers (over HTTPS with a valid certificate when a domain is set, and that plain HTTP redirects), that the gateway refuses a call without a token, that it accepts one with a token, and that nothing but ports 22, 80, and 443 listens on a public interface; and prints a 30-day access link for viewers.

`sudo streamsense-deploy verify` repeats the checks; `sudo streamsense-deploy status` is `docker compose ps`; `sudo streamsense-deploy link` mints another access link and `sudo streamsense-deploy token` a bare token (`--ttl-seconds` on either to change the lifetime).

## Domain and HTTPS

The console can be served at a name of your own instead of the address. It needs HTTPS: a `.dev` name in particular is on the HSTS preload list every browser ships, so browsers refuse plain HTTP for it outright. Caddy, the edge container in the overlay, obtains and renews a Let's Encrypt certificate on its own once it knows the name.

1. **Point the name at the VM.** In your DNS provider (for `streamsense.dev` registered through Vercel: the Vercel dashboard, Domains, the domain, DNS Records) add an `A` record with name `@` (the bare domain) and value the VM's address from `terraform output external_ip`. If the domain is attached to a Vercel project, its records for `@` point at Vercel and must be removed or replaced, or Vercel keeps answering. Add a second `A` record named `www` with the same value only if you also want `www.streamsense.dev`, and then list both names in the Caddyfile. Wait until `nslookup streamsense.dev` on your machine returns the VM's address.
2. **Tell the stack the name.** Add `STREAMSENSE_DOMAIN=streamsense.dev` to `/etc/streamsense/twitch.env` on the VM (the file also holds the image tag; its name is historical).
3. **Deploy.** `sudo streamsense-deploy`. It first checks that the name resolves to this VM (Let's Encrypt must reach port 80 at the name to issue the certificate) and stops with a clear message if not. On the first start Caddy asks Let's Encrypt for the certificate, which takes up to a minute; the verify step waits for it, then confirms `https://streamsense.dev/healthz`, the redirect from port 80, and the gateway checks over HTTPS. The certificate and its renewals live in the `caddy-data` volume, so stops, starts, and redeploys never request a new one.

From then on the printed URL and the sharing instructions use `https://streamsense.dev/`, and the console uses `wss` for its subscriptions on its own because it derives the scheme from the page. Removing the `STREAMSENSE_DOMAIN` line and redeploying returns to plain HTTP at the address.

What changes for security: the console is now findable by name and will be probed by scanners within hours. What they reach is the console shell, and a gateway that answers 401 for everything under `/graphql`, `/api`, and `/ml` without a token; every admin UI and service port stays on the loopback. The bearer token remains the access control, so keep giving it only to people you would give the address to. Let's Encrypt publishes every certificate it issues to public transparency logs, so the name itself is public the moment the certificate exists.

## Sharing the console

Send a viewer the access link `streamsense-deploy` printed:

```
https://streamsense.dev/#token=<token>
```

Opening it signs that browser in: the console keeps the token in local storage and removes it from the address bar, so a copied or bookmarked URL does not carry it on. The token sits in the URL fragment, which browsers never send, so it does not appear in Caddy's or nginx's access logs. Without a token, or once it has run out, the console shows a sign-in page instead of the app; the link (or the bare token) pasted there signs in as well, and the sidebar's Access entry shows how long it is good for and signs out. Anyone with a link can read everything the console shows and use the manual ingest routes within the rate limits; hand links to people you would hand a password to.

## Between demos

Stop the VM when nobody is watching; the disk, the models, the data, and the address stay:

```bash
gcloud compute instances stop streamsense-demo --zone us-central1-a
gcloud compute instances start streamsense-demo --zone us-central1-a
```

Every container has `restart: unless-stopped`, so a started VM brings the stack back on its own in a few minutes; `sudo streamsense-deploy verify` confirms it. Do not `terraform destroy` between demos: that removes the disk and the next apply starts from an empty stack with a cold model cache.

## Updating

Every merge to `main` publishes new images tagged with the commit SHA and, once all eleven have pushed, moves the `main` tags to them as one set. On the VM:

```bash
sudo streamsense-deploy                                # latest main
sudo STREAMSENSE_IMAGE_TAG=<sha> streamsense-deploy    # a specific commit, for example to roll back
```

The tag decides the checkout too: the script checks out the same ref before starting, because the config-server serves `config-server/config-repo` from the checkout and that must be the config the images were built with. `STREAMSENSE_REPO_REF` overrides the ref when they must differ. A `STREAMSENSE_IMAGE_TAG=` line in `/etc/streamsense/twitch.env` pins the tag between runs; a value in the shell wins over it.

Compose recreates only the containers whose image or configuration changed. Kafka, Postgres, and MinIO keep their volumes.

Changing the VM itself (machine type, disk size, firewall ranges) is a `terraform apply` after editing `terraform.tfvars`; a machine-type change stops and starts the VM.

## Admin UIs

They bind to the VM's loopback. Forward them over SSH and open them locally:

```bash
gcloud compute ssh streamsense-demo --zone us-central1-a -- -N \
  -L 3001:localhost:3001 -L 9090:localhost:9090 -L 9411:localhost:9411 -L 8088:localhost:8088 -L 9001:localhost:9001
```

Grafana at `http://localhost:3001` (user `admin`, password in `/opt/streamsense/secrets/GRAFANA_ADMIN_PASSWORD` on the VM), Prometheus at 9090, Zipkin at 9411, Kafka UI at 8088, MinIO console at 9001 (credentials in the two `STREAMSENSE_FRAME_STORAGE_*` secret files).

## Logs and troubleshooting

```bash
cd /opt/streamsense
sudo docker compose --env-file /etc/streamsense/twitch.env -f docker-compose.yml -f docker-compose.prod.yml logs -f --tail 100 video-capture-service
sudo journalctl -u google-startup-scripts --no-pager | tail -50    # first-boot provisioning
```

- **A container never turns healthy.** `streamsense-deploy` prints its last log lines on timeout; a service listed as `missing` has no container at all (`docker compose ps --all`). The usual causes are a service waiting on another (the gateway waits for everything) and, on the first run, ml-engine still downloading a model.
- **Video capture reports no frames.** Twitch sometimes refuses stream playlists to cloud address ranges. Check the capture log for a 403 or "content restricted" from streamlink. If so, the demo falls back to the VOD replay alias (`docs/replay-runbook.md`) and live capture stays on a home connection; there is no fix on the VM side.
- **No certificate, or the browser warns.** `sudo docker compose ... logs caddy` shows the Let's Encrypt exchange. The usual causes: the `A` record does not point at this VM yet (the deploy checks this first), port 80 is not reachable from the internet (`allowed_http_cidrs` narrowed to your own address blocks the challenge too), or Let's Encrypt's rate limit after repeated failed attempts (five per hour per name; wait an hour).
- **Out of memory.** `docker stats` shows each container against its limit from `docker-compose.prod.yml`. Raise a limit there in a branch rather than on the VM, so the change survives the next deploy.
- **Out of disk.** `docker system df`; `docker image prune` removes superseded images. Model caches and data live in named volumes and are not pruned.

## Teardown

```bash
cd terraform && terraform destroy
```

Removes the VM, its disk, the address, the network, the firewall rules, and the service account. The Compute Engine API stays enabled on the project. The published images stay on GitHub Container Registry, and the DNS record at your provider stays until you delete it.
