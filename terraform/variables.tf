variable "project_id" {
  description = "GCP project that owns every resource. Billing and the Compute Engine API must be enabled on it."
  type        = string
}

variable "region" {
  description = "Region for the subnetwork and the static address."
  type        = string
  default     = "us-central1"
}

variable "zone" {
  description = "Zone for the VM; must be inside var.region."
  type        = string
  default     = "us-central1-a"
}

variable "instance_name" {
  description = "Name of the VM and prefix of the network resources."
  type        = string
  default     = "streamsense-demo"
}

variable "machine_type" {
  description = "Machine type. The Compose overlay's memory limits sum to about 25 GiB, so 32 GiB is the comfortable size with live capture and Whisper on CPU."
  type        = string
  default     = "e2-standard-8"
}

variable "boot_disk_gb" {
  description = "Boot disk size. Images, models, Kafka, Postgres, and MinIO all live on it; 100 GB leaves room to grow."
  type        = number
  default     = 100
}

variable "allowed_ssh_cidrs" {
  description = "Source ranges that may reach port 22. Your own address as /32; nothing else."
  type        = list(string)

  validation {
    condition     = length(var.allowed_ssh_cidrs) > 0 && !contains(var.allowed_ssh_cidrs, "0.0.0.0/0")
    error_message = "allowed_ssh_cidrs must list at least one range and must not contain 0.0.0.0/0."
  }
}

variable "allowed_http_cidrs" {
  description = "Source ranges that may reach the console on port 80. The default lets anyone with the address and a gateway token in; narrow it to your viewers' addresses if you know them."
  type        = list(string)
  default     = ["0.0.0.0/0"]
}

variable "repo_url" {
  description = "Repository the startup script clones into /opt/streamsense."
  type        = string
  default     = "https://github.com/8wali8/StreamSense-Production.git"
}

variable "repo_ref" {
  description = "Branch or tag the startup script checks out. The deploy script pulls it again on every run."
  type        = string
  default     = "main"
}

variable "labels" {
  description = "Labels applied to the VM and the disk, for the billing report."
  type        = map(string)
  default = {
    app = "streamsense"
    env = "demo"
  }
}
