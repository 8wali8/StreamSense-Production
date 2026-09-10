#!/usr/bin/env bash
# Deploys, verifies, and operates the hosted demo on the VM that terraform/ creates. Runs as root
# (sudo) on the VM; docs/hosting.md is the runbook around it.
#
# The startup script links it as /usr/local/bin/streamsense-deploy.
#
#   streamsense-deploy [deploy]   pull the repository, refresh secrets, pull the images, start the stack, verify
#   streamsense-deploy verify     health of every container, then the console and the gateway through the edge
#   streamsense-deploy token      print a bearer token for the console (TTL from --ttl-seconds, default 30 days)
#   streamsense-deploy status     docker compose ps
#
# Environment (all optional):
#   STREAMSENSE_DIR        checkout to deploy from                     (default /opt/streamsense)
#   STREAMSENSE_ENV_FILE   Twitch settings, passed as --env-file      (default /etc/streamsense/twitch.env)
#   STREAMSENSE_IMAGE_TAG  image tag to run: "main" or a commit SHA   (default: the env file's value, else main)
#   STREAMSENSE_REPO_REF   git ref to check out                       (default: the image tag, so the
#                          config-repo the config-server serves matches the images that read it)
#   STREAMSENSE_DOMAIN     name Caddy serves over HTTPS               (default: the env file's value, else
#                          none, which means plain HTTP on the VM's address)
set -euo pipefail

STREAMSENSE_DIR="${STREAMSENSE_DIR:-/opt/streamsense}"
STREAMSENSE_ENV_FILE="${STREAMSENSE_ENV_FILE:-/etc/streamsense/twitch.env}"
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

# Which images to run and which commit to check out. Compose gives the shell precedence over
# --env-file, so the tag is exported only after the env file has had its say; the ref follows
# the tag unless set explicitly, so config-server serves the config-repo of the commit the
# images were built from (the base file bind-mounts it from the checkout).
env_file_value() {
  grep -E "^$1=" "$STREAMSENSE_ENV_FILE" | tail -n 1 | cut -d= -f2- | tr -d '[:space:]"' || true
}

resolve_versions() {
  local from_file
  from_file="$(env_file_value STREAMSENSE_IMAGE_TAG)"
  export STREAMSENSE_IMAGE_TAG="${STREAMSENSE_IMAGE_TAG:-${from_file:-main}}"
  STREAMSENSE_REPO_REF="${STREAMSENSE_REPO_REF:-$STREAMSENSE_IMAGE_TAG}"
  # The domain follows the same shell-then-file rule; empty means HTTP on the address.
  from_file="$(env_file_value STREAMSENSE_DOMAIN)"
  export STREAMSENSE_DOMAIN="${STREAMSENSE_DOMAIN:-$from_file}"
}

# Where viewers open the console, and how this script reaches the edge from inside the VM.
console_url() {
  if [ -n "$STREAMSENSE_DOMAIN" ]; then
    echo "https://$STREAMSENSE_DOMAIN/"
  else
    echo "http://$(external_ip)/"
  fi
}

# With a domain, Let's Encrypt must reach port 80 at the name, so the A record has to point at
# this VM before the stack starts; catching that here beats a certificate error minutes later.
require_dns() {
  [ -n "$STREAMSENSE_DOMAIN" ] || return 0
  local ip resolved
  ip="$(external_ip)"
  resolved="$(getent ahostsv4 "$STREAMSENSE_DOMAIN" | awk '{print $1}' | sort -u | tr '\n' ' ')"
  case " $resolved" in
    *" $ip "*) echo "DNS: $STREAMSENSE_DOMAIN -> $ip" ;;
    *) die "DNS for $STREAMSENSE_DOMAIN resolves to '${resolved:-nothing}', not this VM ($ip); add or fix the A record and wait for it to propagate" ;;
  esac
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
  # The signing secret goes through the environment, never the command line (visible in /proc).
  STREAMSENSE_GATEWAY_AUTH_HMAC_SECRET="$(hmac_secret)" \
    python3 "$STREAMSENSE_DIR/tools/mint-jwt.py" --subject demo-viewer --ttl-seconds "$ttl"
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
  local expected pending
  # Every service the two files define must have a container; a missing one is not healthy.
  expected="$(compose config --services | sort | tr '\n' ' ')"
  while :; do
    # One JSON object per line. A service with a healthcheck must report healthy; one without must
    # be running; the topics init job must have exited 0; a service with no row is missing.
    pending="$(compose ps --all --format json | EXPECTED="$expected" python3 -c '
import json, os, sys
expected = set(os.environ["EXPECTED"].split())
seen = set()
pending = []
for line in sys.stdin:
    line = line.strip()
    if not line:
        continue
    c = json.loads(line)
    service, state, health, code = c["Service"], c["State"], c.get("Health", ""), c.get("ExitCode", 0)
    seen.add(service)
    if service == "kafka-topics-init":
        ok = state == "exited" and code == 0
    elif health:
        ok = health == "healthy"
    else:
        ok = state == "running"
    if not ok:
        suffix = (":" + health) if health else ""
        pending.append(f"{service}({state}{suffix})")
for service in sorted(expected - seen):
    pending.append(f"{service}(missing)")
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
        case "$s" in *"(missing)") continue ;; esac
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
  local base token
  # Requests go to Caddy on this VM but carry the public name, so the certificate and the
  # HTTPS redirect are exercised without depending on DNS from inside the VM.
  local -a via=()
  if [ -n "$STREAMSENSE_DOMAIN" ]; then
    base="https://$STREAMSENSE_DOMAIN"
    via=(--resolve "$STREAMSENSE_DOMAIN:443:127.0.0.1" --resolve "$STREAMSENSE_DOMAIN:80:127.0.0.1")
    log "Verifying the console and the gateway through Caddy at $STREAMSENSE_DOMAIN"

    # The first start obtains the certificate from Let's Encrypt; allow it a few minutes.
    local deadline=$(( $(date +%s) + 300 )) answer=""
    while :; do
      answer="$(curl -fsS -m 10 "${via[@]}" "$base/healthz" 2>/dev/null || true)"
      [ "$answer" = "ok" ] && break
      if [ "$(date +%s)" -ge "$deadline" ]; then
        compose logs --no-color --tail 40 caddy >&2 || true
        die "no valid certificate for $STREAMSENSE_DOMAIN after 5 minutes; see the caddy log above"
      fi
      echo "waiting for the certificate for $STREAMSENSE_DOMAIN"
      sleep 15
    done
    echo "console /healthz over HTTPS: ok (certificate valid)"

    local code
    code="$(curl -sS -m 10 -o /dev/null -w '%{http_code}' "${via[@]}" "http://$STREAMSENSE_DOMAIN/healthz")"
    case "$code" in
      301|308) echo "plain HTTP redirects to HTTPS: $code" ;;
      *) die "expected a redirect from http://$STREAMSENSE_DOMAIN/, got $code" ;;
    esac
  else
    base="http://127.0.0.1"
    log "Verifying the console and the gateway through Caddy on port 80"
    if [ "$(curl -fsS -m 5 "$base/healthz")" != "ok" ]; then
      die "console /healthz did not answer ok"
    fi
    echo "console /healthz: ok"
  fi

  # Auth is on: an unauthenticated GraphQL call must be refused.
  local code
  code="$(curl -sS -m 10 -o /dev/null -w '%{http_code}' "${via[@]}" -H 'Content-Type: application/json' \
    -d '{"query":"query { health }"}' "$base/graphql")"
  if [ "$code" != "401" ]; then
    die "expected 401 without a token, got $code"
  fi
  echo "gateway without a token: 401 (auth is on)"

  token="$(mint_token 600)"
  if ! curl -fsS -m 10 "${via[@]}" -H 'Content-Type: application/json' -H "Authorization: Bearer $token" \
      -d '{"query":"query { health }"}' "$base/graphql" | grep -q '"health":"ok"'; then
    die "gateway did not answer the health query with a valid token"
  fi
  echo "gateway with a token: health ok"

  # Only Caddy is reachable from outside; nothing else may listen on a public interface.
  local exposed
  exposed="$(ss -Hltn | awk '{print $4}' | grep -Ev '^(127\.0\.0\.1|\[::1\]|127\.0\.0\.53%lo|127\.0\.0\.54):' | grep -Ev ':(22|80|443)$' || true)"
  if [ -n "$exposed" ]; then
    die "unexpected public listeners: $exposed"
  fi
  echo "public listeners: 22, 80, and 443 only"
  echo
  echo "Console: $(console_url)"
}

print_sharing_instructions() {
  local url token
  url="$(console_url)"
  token="$(mint_token)"
  cat <<MSG

Share with a viewer:

  URL    $url
  Token  $token

The console reads its bearer token from local storage. In the browser, open the developer tools
console on $url once and run:

  localStorage.setItem("streamsense.authToken", "$token"); location.reload();

The token is valid for 30 days; run "streamsense-deploy token" for a new one.
MSG
}

cmd_deploy() {
  require_root
  require_env_file
  resolve_versions
  require_dns
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
  resolve_versions
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
  resolve_versions
  cd "$STREAMSENSE_DIR"
  compose ps
}

case "${1:-deploy}" in
  deploy) cmd_deploy ;;
  verify) cmd_verify ;;
  token) shift; cmd_token "$@" ;;
  status) cmd_status ;;
  -h|--help|help) sed -n '2,21p' "$0" ;;
  *) die "unknown command ${1}; try deploy, verify, token, status" ;;
esac
