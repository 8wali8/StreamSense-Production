# One VM for the hosted demo (docs/planning/cloud-hosting.md): its own VPC and subnet, a static
# external address, a firewall that admits SSH from the operator and HTTP from the viewers, a
# service account with no permissions beyond writing logs and metrics, and a startup script that
# installs Docker and clones the repository. Everything else happens on the VM through
# tools/deploy/deploy.sh.

locals {
  network_tag = "${var.instance_name}-web"
}

# The Compute Engine API. disable_on_destroy is off so a `terraform destroy` removes the VM
# without switching the API off under other resources in the project.
resource "google_project_service" "compute" {
  service            = "compute.googleapis.com"
  disable_on_destroy = false
}

# A dedicated network instead of the project's default one, so the firewall below is the
# whole story of what can reach the VM.
resource "google_compute_network" "this" {
  name                    = "${var.instance_name}-net"
  auto_create_subnetworks = false

  depends_on = [google_project_service.compute]
}

resource "google_compute_subnetwork" "this" {
  name                     = "${var.instance_name}-subnet"
  network                  = google_compute_network.this.id
  region                   = var.region
  ip_cidr_range            = "10.10.0.0/24"
  private_ip_google_access = true
}

resource "google_compute_firewall" "ssh" {
  name          = "${var.instance_name}-allow-ssh"
  network       = google_compute_network.this.name
  direction     = "INGRESS"
  source_ranges = var.allowed_ssh_cidrs
  target_tags   = [local.network_tag]

  allow {
    protocol = "tcp"
    ports    = ["22"]
  }
}

resource "google_compute_firewall" "http" {
  name          = "${var.instance_name}-allow-http"
  network       = google_compute_network.this.name
  direction     = "INGRESS"
  source_ranges = var.allowed_http_cidrs
  target_tags   = [local.network_tag]

  allow {
    protocol = "tcp"
    ports    = ["80"]
  }
}

# The address outlives the VM: stopping and starting the instance keeps it, and so does
# replacing the instance, so the URL shared with viewers stays valid.
resource "google_compute_address" "this" {
  name   = "${var.instance_name}-ip"
  region = var.region

  depends_on = [google_project_service.compute]
}

# The VM's identity. No IAM roles beyond writing its own logs and metrics; nothing on the VM
# needs to call Google APIs.
resource "google_service_account" "vm" {
  account_id   = "${var.instance_name}-vm"
  display_name = "StreamSense demo VM"
}

resource "google_project_iam_member" "vm_logging" {
  project = var.project_id
  role    = "roles/logging.logWriter"
  member  = "serviceAccount:${google_service_account.vm.email}"
}

resource "google_project_iam_member" "vm_monitoring" {
  project = var.project_id
  role    = "roles/monitoring.metricWriter"
  member  = "serviceAccount:${google_service_account.vm.email}"
}

resource "google_compute_instance" "this" {
  name         = var.instance_name
  machine_type = var.machine_type
  zone         = var.zone
  tags         = [local.network_tag]
  labels       = var.labels

  # Changing the machine type or the disk means a stop; allow Terraform to do that in place.
  allow_stopping_for_update = true

  boot_disk {
    initialize_params {
      image  = "ubuntu-os-cloud/ubuntu-2404-lts-amd64"
      size   = var.boot_disk_gb
      type   = "pd-balanced"
      labels = var.labels
    }
  }

  network_interface {
    subnetwork = google_compute_subnetwork.this.id

    # The one public address; the firewall decides who can use it. Trivy's GCP-0031 wants no
    # public address at all, which for this VM would mean a load balancer or a bastion in front
    # of a demo shared by its address; the firewall rules above are the control instead.
    #trivy:ignore:AVD-GCP-0031
    access_config {
      nat_ip = google_compute_address.this.address
    }
  }

  service_account {
    email  = google_service_account.vm.email
    scopes = ["cloud-platform"]
  }

  shielded_instance_config {
    enable_secure_boot          = true
    enable_vtpm                 = true
    enable_integrity_monitoring = true
  }

  metadata = {
    # SSH through IAM (gcloud compute ssh) instead of project-wide keys in metadata.
    enable-oslogin = "TRUE"
    # Nothing reads the serial console; leave it off.
    serial-port-enable = "FALSE"
  }

  metadata_startup_script = templatefile("${path.module}/startup.sh", {
    repo_url = var.repo_url
    repo_ref = var.repo_ref
  })

  scheduling {
    # A demo VM that is stopped by hand between sessions; let Google migrate it during
    # maintenance and restart it after a crash so a started VM stays up.
    automatic_restart   = true
    on_host_maintenance = "MIGRATE"
  }

  depends_on = [
    google_compute_firewall.ssh,
    google_compute_firewall.http,
  ]
}
