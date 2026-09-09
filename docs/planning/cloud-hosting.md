# Cloud hosting plan: the demo stack on one GCP VM

StreamSense runs today on a developer machine under Docker Compose, and on a local kind cluster from the manifests under `k8s/`. This plan moves the Compose stack onto a single Google Compute Engine VM so the console can be shared with a few people, live Twitch capture included. It is a portfolio demo built to gain experience with the tooling, not a production launch: no domain, no TLS, a handful of viewers, and the VM stopped between demos.

The work lands as four small branches, each a pull request to `main`, stacked in order so every PR shows only its own diff. The same conventions as the hardening series apply (`production-hardening.md`): one concern per branch, a note under `branches/` with what changed and how it was verified, nothing merged by the agent.

## Decisions

| Question | Decision | Why |
|---|---|---|
| Cloud | GCP | Cheaper than the AWS equivalent for the same shape; no preference otherwise. AWS would differ only in the Terraform provider and the firewall resource. |
| Shape | Docker Compose on one VM | It is the repository's primary mode and the one CI proves end to end (`docker-smoke`). Kubernetes on the same VM is phase two, see below. |
| Machine | `e2-standard-8` (8 vCPU, 32 GB), 100 GB balanced persistent disk, Ubuntu 24.04 LTS | The Kubernetes limits sum to about 22 GB with 6 GB for ml-engine; live capture adds ffmpeg and Whisper on CPU. At roughly $0.27 per hour, a month of demos is a few dollars because the VM is stopped in between. |
| Lifecycle | Stop and start between demos, never destroy | Stopping keeps the disk: downloaded models (2 to 3 GB), Kafka data, Postgres, MinIO. Destroying means a cold model download and an empty stack on every demo. `terraform destroy` is for the end of the project. |
| Domain and TLS | None. Plain HTTP on a static external IP, port 80 | Not needed for a demo shared with a few people. The console derives `ws` or `wss` from the page origin, so nothing in the code changes when a domain and Caddy are added later. |
| Access control | Gateway JWT auth on; viewers get the IP and a token minted with `tools/mint-jwt.py`; firewall allows 22 and 80 only, optionally from listed CIDRs | The auth path already exists end to end (HS256 secret, protected paths, token field in the console) and this exercises it for real. Grafana, Zipkin, MinIO console, and Kafka UI bind to localhost on the VM and are reached over an SSH tunnel. |
| Twitch | Live capture on the VM | The point of the demo. Credentials live in `/etc/streamsense/twitch.env` on the VM, outside the repository checkout, loaded through `env_file`. |
| Images | Built and pushed by CI to GitHub Container Registry, `ghcr.io/8wali8/streamsense/<service>`, tagged with the commit SHA and `main` | The VM never builds; it pulls a SHA that CI tested. The repository is public, so the packages are public and the VM needs no registry credentials. Google Artifact Registry with keyless auth from Actions is a phase-two swap. |
| Infrastructure as code | Terraform, local state | Declarative create, plan, and destroy is the standard way cloud resources are managed and the more valuable thing to learn than a gcloud script. One person, one VM: local state is enough. The in-VM setup is a startup script inside the Terraform, the usual split. |

## Ground rules

- **Branch names are `cloud/NN-slug`**, numbered in review order, one worktree each under `StreamSense-worktrees/cloud-NN-slug`. Each PR is opened with the previous branch as its base; GitHub retargets it to `main` when the base merges.
- **Nothing in the application changes.** These branches add a CI job, a Compose override, a `terraform/` directory, a deploy script, and documentation. A service that needs a code change to run on the VM gets its own `fix/` branch first.
- **Every credential stays out of git.** Terraform variable files (`*.tfvars`), state (`*.tfstate*`), the `.terraform/` directory, and the Twitch env file are git-ignored; committed examples document their shape, the same way `secrets/*.example` and `k8s/secrets/streamsense.env.example` do.
- **Pins as everywhere else**: new GitHub Actions by commit SHA with the version in a comment, images by tag and digest, Terraform provider and required version pinned.
- **CI validates what it can.** The override renders with `docker compose config`, the Terraform passes `fmt -check` and `validate`, and the existing Trivy misconfiguration gate scans both. What only a real VM can prove is proven on a real VM and recorded in the branch note.

## Verification ladder

| Rung | Command | Applies when |
|---|---|---|
| Workflow | `actionlint .github/workflows/ci.yml` | The workflow changed |
| Compose render | `make secrets && docker compose -f docker-compose.yml -f docker-compose.prod.yml config -q` | The override or the base file changed |
| Terraform static | `terraform fmt -check -recursive && terraform init -backend=false && terraform validate` in `terraform/` | Anything under `terraform/` changed |
| Misconfiguration gate | `trivy fs --scanners misconfig --severity HIGH,CRITICAL --exit-code 1 .` with `.trivyignore`, the CI settings | Compose or Terraform changed |
| Publish dry run | `workflow_dispatch` of the publish job from the branch: every image builds and pushes under the branch's SHA tag | Branch 01 |
| Cloud | `terraform apply`, then the deploy script on the VM, then `tools/smoke/compose_smoke.py` against the VM and a live capture session; the note records the machine, the timings, and the memory each container settled at | Branches 03 and 04 |

## Branch sequence

| # | Branch | Scope | Base | Effort | Risk | Proof |
|---|---|---|---|---|---|---|
| 00 | `cloud/00-plan` | This document; planning index entry | main | 1 hour | none | Agree the decisions and the order |
| 01 | `cloud/01-image-publish` | A `publish-images` job in `ci.yml`: runs on pushes to `main` after `ci-ok` succeeds and on `workflow_dispatch`; packages the Java reactor once; logs into GHCR with the workflow token (`packages: write` on this job only); builds and pushes the eleven images with the Actions build cache, tagged `<sha>` and, on `main`, `main`. Packages made public once in the GitHub UI after the first push. | 00 | half day | low: inert for every other job | A dispatched run from the branch pushes eleven images; `docker pull` of one by SHA works anonymously |
| 02 | `cloud/02-compose-prod` | `docker-compose.prod.yml`: every service pulls `ghcr.io/8wali8/streamsense/<service>:${STREAMSENSE_IMAGE_TAG:-main}`; host ports removed except the frontend on `80:8080`; Grafana, Zipkin, MinIO console, and Kafka UI on `127.0.0.1` only; memory limits copied from the Kubernetes manifests; `restart: unless-stopped`; gateway auth on, GraphiQL off, trusted proxy hops `1`; Twitch settings from `env_file: /etc/streamsense/twitch.env`. CI renders the pair. | 01 | half day | low: a file nobody loads unless asked | Render rung; `docker compose ... up` on the developer machine with the override and a pulled tag boots to healthy |
| 03 | `cloud/03-terraform` | `terraform/`: Google provider, `required_version`, variables (project, region, zone, machine type, disk size, allowed CIDRs), a static external address, a firewall for 22 and 80, the VM with a startup script that installs Docker Engine and the Compose plugin, creates `/etc/streamsense`, and clones the repository; outputs the IP; `terraform.tfvars.example`; a `terraform-checks` CI job path-filtered to `terraform/**`. Findings from the Trivy misconfiguration gate on the new files are fixed or suppressed with a reason. | 02 | 1 day | medium: first real cloud resources, learning curve | Static rungs; `terraform apply` creates the VM and `ssh` shows Docker running; `terraform destroy` removes everything |
| 04 | `cloud/04-deploy-and-runbook` | `tools/deploy/deploy.sh` for the VM: pull the repository, `make secrets`, pull the images at the requested tag, `docker compose` with the override, wait for health, run the Compose smoke script, print the console URL and a minted token. `docs/hosting.md`: create, first boot and model warm-up, sharing the IP and token, stop and start, updating to a new SHA, logs and tunnels, teardown, cost. CLAUDE.md gains a hosting section and the planning index is updated. | 03 | 1 day | medium: the first real deploy is where surprises surface | Cloud rung end to end, with a live capture session recorded in the note |

About three days of work in total, plus roughly an hour on the owner's side beforehand: a GCP project with billing, the Compute Engine API enabled, `gcloud` and `terraform` installed locally, and `gcloud auth application-default login`.

## Known risks

- **Twitch and datacenter addresses.** Twitch sometimes refuses stream playlists to cloud IP ranges. streamlink from a GCE address usually works; if it does not, the demo falls back to the VOD replay alias and live capture stays on the developer machine. Branch 04 tests this first, before anything else on the VM.
- **Memory limits are a first guess.** The Kubernetes numbers were set for kind, not for live capture plus Whisper. Branch 04 records what each container settles at and adjusts the override once.
- **Cold start.** First boot downloads the models and starts eight JVMs; expect several minutes before the console is useful. The runbook says so, and stopping rather than destroying the VM avoids repeating it.
- **Trivy on Terraform.** The misconfiguration gate will flag a VM with a public IP and no shielded-VM or OS Login settings. Branch 03 turns on what is free (shielded VM, OS Login, no default service-account scopes) and suppresses the rest with the reason written down.

## Phase two (not in this plan)

Each of these is its own branch after 04 merges, in whatever order the experience goal suggests:

- **k3s on the same VM** using the existing `k8s/` manifests: image references move from `sprint9` to the GHCR tags, the `*.streamsense.local` ingress hosts become the VM address, and the default-deny network policies get enforced for real (k3s ships a policy controller). The biggest learning payoff.
- **Google Artifact Registry** with Workload Identity Federation from GitHub Actions, replacing GHCR.
- **A domain and Caddy** in front of the frontend for automatic TLS; the console then uses `wss` on its own.
- **Image scanning in the publish job** (`trivy image` on each pushed image), deferred in `branches/14-supply-chain.md` until images lived in a registry.
- **A budget alert** on the GCP project and a scheduled stop, so a forgotten VM costs a bounded amount.
