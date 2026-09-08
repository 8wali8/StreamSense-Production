#!/usr/bin/env bash
# Deploys, verifies, and operates the hosted demo on the VM that terraform/ creates. Runs as root
# (sudo) on the VM; docs/hosting.md is the runbook around it.
#
#   deploy.sh [deploy]   pull the repository, refresh secrets, pull the images, start the stack, verify
#   deploy.sh verify     health of every container, then the console and the gateway through port 80
#   deploy.sh token      print a bearer token for the console (TTL from --ttl-seconds, default 30 days)
#   deploy.sh status     docker compose ps
#
# Environment (all optional):
#   STREAMSENSE_DIR        checkout to deploy from            (default /opt/streamsense)
#   STREAMSENSE_ENV_FILE   Twitch settings, --env-file       (default /etc/streamsense/twitch.env)
#   STREAMSENSE_REPO_REF   branch or tag to check out        (default main)
#   STREAMSENSE_IMAGE_TAG  image tag to run: "main" or a SHA (default main)
set -euo pipefail

STREAMSENSE_DIR="${STREAMSENSE_DIR:-/opt/streamsense}"
STREAMSENSE_ENV_FILE="${STREAMSENSE_ENV_FILE:-/etc/streamsense/twitch.env}"
STREAMSENSE_REPO_REF="${STREAMSENSE_REPO_REF:-main}"
export STREAMSENSE_IMAGE_TAG="${STREAMSENSE_IMAGE_TAG:-main}"
HEALTH_TIMEOUT_SECONDS="${HEALTH_TIMEOUT_SECONDS:-900}"

log() { printf '\n== %s\n' "$*"; }
die() { printf 'deploy.sh: %s\n' "$*" >&2; exit 1; }

compose() {
  docker compose --env-file "$STREAMSENSE_ENV_FILE" -f docker-compose.yml -f docker-compose.prod.yml "$@"
}

require_root() {
  [ "$(id -u)" -eq 0 ] || die "run with sudo: Docker and /etc/streamsense need root"
}

require_env_file() {
  if [ ! -f "$STREAMSENSE_ENV_FILE" ]; then
    die "missing $STREAMSENSE_ENV_FILE: copy tools/deploy/twitch.env.example there and fill in the Twitch values"
  fi
  # The whole file is Twitch credentials; nobody but root reads it.
  chmod 0600 "$STREAMSENSE_ENV_FILE"
}

external_ip() {
  # GCE metadata server; falls back to the first address when not on GCE.
  curl -fsS -m 2 -H 'Metadata-Flavor: Google' \
    'http://metadata.google.internal/computeMetadata/v1/instance/network-interfaces/0/access-configs/0/external-ip' \
    2>/dev/null || hostname -I | awk '{print $1}'
}

hmac_secret() {
  tr -d '[:space:]' < "$STREAMSENSE_DIR/secrets/STREAMSENSE_GATEWAY_AUTH_HMAC_SECRET"
}

mint_token() {
  local ttl="${1:-2592000}"
  python3 "$STREAMSENSE_DIR/tools/mint-jwt.py" --secret "$(hmac_secret)" --subject demo-viewer --ttl-seconds "$ttl"
}

update_checkout() {
  log "Updating $STREAMSENSE_DIR to $STREAMSENSE_REPO_REF"
  git -C "$STREAMSENSE_DIR" fetch --quiet --prune origin
  git -C "$STREAMSENSE_DIR" checkout --quiet "$STREAMSENSE_REPO_REF"
  # A tag or a detached SHA has no upstream; only a branch pulls.
  if git -C "$STREAMSENSE_DIR" symbolic-ref -q HEAD >/dev/null; then
    git -C "$STREAMSENSE_DIR" pull --quiet --ff-only
  fi
  git -C "$STREAMSENSE_DIR" log -1 --oneline
}

wait_for_health() {
  log "Waiting up to ${HEALTH_TIMEOUT_SECONDS}s for every container to be healthy"
  local deadline=$(( $(date +%s) + HEALTH_TIMEOUT_SECONDS ))
  local pending
  while :; do
    # One JSON object per line. A service with a healthcheck must report healthy; one without must
    # be running; the topics init job must have exited 0.
    pending="$(compose ps --all --format json | python3 -c '
import json, sys
pending = []
for line in sys.stdin:
    line = line.strip()
    if not line:
        continue
    c = json.loads(line)
    service, state, health, code = c["Service"], c["State"], c.get("Health", ""), c.get("ExitCode", 0)
    if service == "kafka-topics-init":
        ok = state == "exited" and code == 0
    elif health:
        ok = health == "healthy"
    else:
        ok = state == "running"
    if not ok:
        suffix = (":" + health) if health else ""
        pending.append(f"{service}({state}{suffix})")
print(" ".join(pending))
')"
    if [ -z "$pending" ]; then
      echo "every container is healthy"
      return 0
    fi
    if [ "$(date +%s)" -ge "$deadline" ]; then
      echo "still not healthy: $pending" >&2
      compose ps
      for s in $pending; do
        echo "--- last log lines of ${s%%(*}" >&2
        compose logs --no-color --tail 30 "${s%%(*}" >&2 || true
      done
      return 1
    fi
    echo "waiting: $pending"
    sleep 15
  done
}

verify_edge() {
  local ip base token
  ip="$(external_ip)"
  base="http://127.0.0.1"
  log "Verifying the console and the gateway through port 80"

  if [ "$(curl -fsS -m 5 "$base/healthz")" != "ok" ]; then
    die "console /healthz did not answer ok"
  fi
  echo "console /healthz: ok"

  # Auth is on: an unauthenticated GraphQL call must be refused.
  local code
  code="$(curl -sS -m 10 -o /dev/null -w '%{http_code}' -H 'Content-Type: application/json' \
    -d '{"query":"query { health }"}' "$base/graphql")"
  if [ "$code" != "401" ]; then
    die "expected 401 without a token, got $code"
  fi
  echo "gateway without a token: 401 (auth is on)"

  token="$(mint_token 600)"
  if ! curl -fsS -m 10 -H 'Content-Type: application/json' -H "Authorization: Bearer $token" \
      -d '{"query":"query { health }"}' "$base/graphql" | grep -q '"health":"ok"'; then
    die "gateway did not answer the health query with a valid token"
  fi
  echo "gateway with a token: health ok"

  # Only the console is reachable from outside; nothing else may listen on a public interface.
  local exposed
  exposed="$(ss -Hltn | awk '{print $4}' | grep -Ev '^(127\.0\.0\.1|\[::1\]|127\.0\.0\.53%lo|127\.0\.0\.54):' | grep -Ev ':(22|80)$' || true)"
  if [ -n "$exposed" ]; then
    die "unexpected public listeners: $exposed"
  fi
  echo "public listeners: 22 and 80 only"
  echo
  echo "Console: http://$ip/"
}

print_sharing_instructions() {
  local ip token
  ip="$(external_ip)"
  token="$(mint_token)"
  cat <<MSG

Share with a viewer:

  URL    http://$ip/
  Token  $token

The console reads its bearer token from local storage. In the browser, open the developer tools
console on http://$ip/ once and run:

  localStorage.setItem("streamsense.authToken", "$token"); location.reload();

The token is valid for 30 days; run "deploy.sh token" for a new one.
MSG
}

cmd_deploy() {
  require_root
  require_env_file
  update_checkout
  cd "$STREAMSENSE_DIR"
  log "Refreshing local secrets (existing files are kept)"
  make --no-print-directory secrets
  log "Pulling images at tag $STREAMSENSE_IMAGE_TAG"
  compose pull --quiet
  log "Starting the stack"
  compose up -d --no-build --remove-orphans
  wait_for_health
  verify_edge
  print_sharing_instructions
}

cmd_verify() {
  require_root
  require_env_file
  cd "$STREAMSENSE_DIR"
  wait_for_health
  verify_edge
}

cmd_token() {
  require_root
  local ttl=2592000
  while [ $# -gt 0 ]; do
    case "$1" in
      --ttl-seconds) ttl="$2"; shift 2 ;;
      *) die "unknown option $1" ;;
    esac
  done
  mint_token "$ttl"
}

cmd_status() {
  require_root
  require_env_file
  cd "$STREAMSENSE_DIR"
  compose ps
}

case "${1:-deploy}" in
  deploy) cmd_deploy ;;
  verify) cmd_verify ;;
  token) shift; cmd_token "$@" ;;
  status) cmd_status ;;
  -h|--help|help) sed -n '2,17p' "$0" ;;
  *) die "unknown command ${1}; try deploy, verify, token, status" ;;
esac
