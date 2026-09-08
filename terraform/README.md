# terraform/

The GCP resources for the hosted demo: a VPC and subnet, a static address, two firewall rules (SSH from the operator, HTTP from the viewers), a service account that can only write logs and metrics, and one shielded `e2-standard-8` VM whose startup script installs Docker and clones this repository into `/opt/streamsense`. The full runbook, including what happens on the VM after this, is `docs/hosting.md`; the reasoning is in `docs/planning/cloud-hosting.md`.

```bash
gcloud auth application-default login          # once; Terraform uses these credentials
cp terraform.tfvars.example terraform.tfvars   # fill in project_id and allowed_ssh_cidrs
terraform init
terraform plan
terraform apply
terraform output                               # external_ip, console_url, ssh/stop/start commands
```

State is local (`terraform.tfstate`, git-ignored): one person, one VM. `terraform destroy` removes everything including the disk; between demos use the `stop_command` and `start_command` outputs instead, which keep the disk, the models, and the address.
