# cloud/03-terraform

Branch 03 of `docs/planning/cloud-hosting.md`: the GCP resources for the demo VM as Terraform, with the checks CI runs on them. Stacked on `cloud/02-compose-prod`.

## What was missing

Nothing in the repository described a machine to run the overlay from branch 02 on. The plan chose Terraform over a gcloud script for the reproducible create, plan, and destroy loop and because it is the more transferable skill.

## What changed

- **`terraform/`** (new), one root module:
  - `versions.tf`: Terraform `~> 1.16`, `hashicorp/google ~> 8.2`, and the provider block reading project, region, and zone from variables. `.terraform.lock.hcl` is committed so CI and the operator resolve the same provider build.
  - `variables.tf`: `project_id` and `allowed_ssh_cidrs` are required (the latter rejects an empty list and `0.0.0.0/0` through a validation block); `zone` must start with `region` followed by a dash, so a mismatched override fails at plan time instead of when the regional subnet is attached; `allowed_http_cidrs` defaults to everyone, since the gateway token is the access control for the console; `machine_type` defaults to `e2-standard-8` and `boot_disk_gb` to 100 for the reasons in the plan; `repo_url` and `repo_ref` feed the startup script; `labels` mark the VM and disk for billing.
  - `main.tf`: the Compute Engine and OS Login APIs (`disable_on_destroy = false`; OS Login was a review finding, since `enable-oslogin` makes the documented `gcloud compute ssh` depend on it); a dedicated VPC with one `10.10.0.0/24` subnet and Private Google Access, so the two firewall rules are the whole story of what reaches the VM (SSH from the operator's ranges, HTTP from the viewers', both matched by network tag); a regional static address that outlives the instance; a service account with only `logging.logWriter` and `monitoring.metricWriter`; and the instance: Ubuntu 24.04 LTS on a balanced disk, shielded VM (secure boot, vTPM, integrity monitoring), OS Login instead of metadata SSH keys, serial console off, `allow_stopping_for_update` so a machine-type change is applied in place, automatic restart with live migration.
  - `startup.sh` (rendered with `templatefile`): runs as root on every boot and is idempotent. First boot installs Docker Engine, the Compose plugin, git, make, openssl, jq, and python3 from Docker's and Ubuntu's apt repositories, clones `repo_url` at `repo_ref` into `/opt/streamsense`, creates `/etc/streamsense` (0700, for the Twitch env the operator copies in), links the deploy script to `/usr/local/bin/streamsense-deploy`, and enables unattended security upgrades. Later boots keep the remote URL in step with `repo_url` but do not move the checked-out ref: that belongs to the deploy script, which pins the checkout to the image tag it runs, and a reboot must not change it under a running stack (a `repo_ref` change is applied by the next deploy).
  - `outputs.tf`: the address, the console URL, and the exact `gcloud` commands to SSH, stop, and start the VM.
  - `terraform.tfvars.example` and a short `README.md`; `.tflint.hcl` next to the module.
- **`.gitignore`**: Terraform state, variable files (`.tfvars` and `.tfvars.json`), the provider directory, and `crash.log` are ignored; the lock file and the `.example` are not.
- **Trivy**: the CI misconfiguration gate scans Terraform. The module produced one finding, `AVD-GCP-0031` (instance has a public address), suppressed inline on the `access_config` block with the reason: no public address would mean a load balancer or a bastion in front of a demo that is shared by its address, and the firewall rules are the control instead.
- **CI**: a `terraform-checks` job, path-filtered to `terraform/**` and listed in `ci-ok`, runs `terraform fmt -check`, `terraform init -backend=false -lockfile=readonly` plus `validate` against the locked provider, and tflint with the `recommended` Terraform preset and the Google ruleset `0.39.0`. The two actions are pinned by commit SHA.
- **CLAUDE.md**: a hosting paragraph naming the directory, the checks, and the runbook.

## Deliberately left alone

- **Local state.** One person and one VM; a GCS backend is a two-line change if that stops being true.
- **No data disk separate from the boot disk.** Everything Docker keeps lives under `/var/lib/docker` on the 100 GB boot disk; a separate disk would let the OS be rebuilt without losing the data, which a demo does not need.
- **No Cloud NAT, no private-only VM, no load balancer.** See the Trivy suppression.
- **No scheduled stop.** Listed in the plan's phase two together with the budget alert.
- **No Workload Identity for CI.** Terraform runs from the operator's machine with application-default credentials; nothing in CI touches GCP.
- The startup script installs the latest Docker Engine from Docker's repository rather than a pinned version: the VM is rebuilt from scratch rarely, and a version pin here would go stale without Renovate seeing it.

## Verification

| Check | Command | Result |
|---|---|---|
| Formatting | `terraform fmt -check -recursive` (Terraform 1.16.1) | clean |
| Validate | `terraform init -backend=false` then `terraform validate` | "The configuration is valid"; provider `google` 8.2.0 locked (re-run after the review fixes) |
| tflint | `tflint --chdir terraform --init && tflint --chdir terraform` (0.64.0, google ruleset 0.39.0) | clean |
| Misconfiguration gate | `trivy fs --scanners misconfig --severity HIGH,CRITICAL --exit-code 1 terraform` (0.74.0) | 0 findings after the inline suppression (1 before: `AVD-GCP-0031`) |
| Workflow lint | `actionlint .github/workflows/ci.yml` | clean |
| Cloud | `terraform apply` in the owner's project | not run from the agent's environment (no GCP credentials); the commands are below and branch 04 records the result |

## What to check by hand

1. `gcloud auth application-default login`, then in `terraform/`: copy the example to `terraform.tfvars`, set `project_id` and your `/32` in `allowed_ssh_cidrs`, and run `terraform init`, `terraform plan` (expect 11 resources), `terraform apply`.
2. `terraform output ssh_command`, run it, and on the VM: `docker version`, `ls /opt/streamsense`, `sudo journalctl -u google-startup-scripts --no-pager | tail` for the first-boot log.
3. `terraform output stop_command` and `start_command`: the address in `terraform output external_ip` is the same after a stop and start.
4. `terraform destroy` removes the ten resources; the Compute API stays enabled.
