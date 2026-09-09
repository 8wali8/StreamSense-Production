# tflint for terraform/: the built-in Terraform rules plus the Google provider ruleset (valid
# machine types, zones, and resource names). Lives next to the module; CI runs `tflint --chdir terraform`.
plugin "terraform" {
  enabled = true
  preset  = "recommended"
}

plugin "google" {
  enabled = true
  version = "0.39.0"
  source  = "github.com/terraform-linters/tflint-ruleset-google"
}
