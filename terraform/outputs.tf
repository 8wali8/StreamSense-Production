output "external_ip" {
  description = "Static address of the VM; the console is http://<external_ip>/ once deployed."
  value       = google_compute_address.this.address
}

output "console_url" {
  description = "Where viewers open the console."
  value       = "http://${google_compute_address.this.address}/"
}

output "ssh_command" {
  description = "SSH through OS Login (the OS Login API is enabled by this module)."
  value       = "gcloud compute ssh ${google_compute_instance.this.name} --zone ${var.zone} --project ${var.project_id}"
}

output "stop_command" {
  description = "Stop the VM between demos; the disk and the address stay."
  value       = "gcloud compute instances stop ${google_compute_instance.this.name} --zone ${var.zone} --project ${var.project_id}"
}

output "start_command" {
  description = "Start the VM for a demo; the stack comes back on its own (restart: unless-stopped)."
  value       = "gcloud compute instances start ${google_compute_instance.this.name} --zone ${var.zone} --project ${var.project_id}"
}
