#!/usr/bin/env bash
# Runs as root on every boot of the VM (metadata_startup_script). Idempotent: installs Docker
# Engine and the Compose plugin from Docker's apt repository on the first boot, keeps the
# repository checkout under /opt/streamsense, and prepares /etc/streamsense for the files the
# operator copies in by hand (the Twitch env). Deployment itself is tools/deploy/deploy.sh.
set -euo pipefail

export DEBIAN_FRONTEND=noninteractive

if ! command -v docker >/dev/null 2>&1; then
  apt-get update -q
  apt-get install -y -q ca-certificates curl git make openssl jq python3
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  . /etc/os-release
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $${VERSION_CODENAME} stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -q
  apt-get install -y -q docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  systemctl enable --now docker
fi

# Operator-supplied files (the Twitch env) live here, outside the checkout and readable by root only.
install -d -m 0700 /etc/streamsense

# First boot clones the repository at repo_ref. Later boots only keep the remote in step with
# the applied repo_url: the checked-out ref belongs to tools/deploy/deploy.sh, which pins the
# checkout to the image tag it deploys, and a boot must not move it under a running stack.
if [ ! -d /opt/streamsense/.git ]; then
  git clone --branch "${repo_ref}" "${repo_url}" /opt/streamsense
else
  git -C /opt/streamsense remote set-url origin "${repo_url}"
fi

# The deploy script as a command on root's PATH (sudo's secure_path includes /usr/local/bin).
ln -sfn /opt/streamsense/tools/deploy/deploy.sh /usr/local/bin/streamsense-deploy

# Unattended security updates for the OS; Docker and the stack are updated by the deploy script.
apt-get install -y -q unattended-upgrades
