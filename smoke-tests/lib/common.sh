# Shared helpers for smoke-tests/scenarios/*.sh. Sourced by run.sh — not meant to be run directly.
# Deliberately no `set -e`: scenarios keep running after an assertion fails so one bad check
# doesn't hide the rest of the results.
set -uo pipefail

SMOKE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT_DIR="$(cd "$SMOKE_DIR/.." && pwd)"

if [[ -f "$ROOT_DIR/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT_DIR/.env"
  set +a
fi

# --- Config (env var, falling back to .env value, falling back to .env.example defaults) ---
GATEWAY_URL="${GATEWAY_URL:-http://localhost:${GATEWAY_PORT:-8080}}"
API_SERVER_URL="${API_SERVER_URL:-http://localhost:${API_SERVER_PORT:-8081}}"
PRICING_SERVICE_URL="${PRICING_SERVICE_URL:-http://localhost:${PRICING_SERVICE_PORT:-8083}}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:${KEYCLOAK_PORT:-8180}}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-gateway-sample}"
SMOKE_CLIENT_ID="${SMOKE_CLIENT_ID:-smoke-tests}"
SMOKE_CLIENT_SECRET="${SMOKE_CLIENT_SECRET:-dev-only-smoke-tests-secret}"
POSTGRES_USER="${POSTGRES_USER:-gatewaysample}"
POSTGRES_DB="${POSTGRES_DB:-gatewaysample}"
API_KEY_PEPPER="${API_KEY_PEPPER:-dev-only-api-key-pepper-change-me}"

DC=(docker compose -f "$ROOT_DIR/docker-compose.infra.yml" -f "$ROOT_DIR/docker-compose.yml")

RUN_ID="smoke-$$-${RANDOM}"

# --- Bookkeeping ---
PASS_COUNT=0
FAIL_COUNT=0
FAILURE_MESSAGES=()
PRICING_SERVICE_STOPPED=0

# provision_api_key runs via `$(...)` command substitution in every call site, i.e. in a
# subshell — appends to a PROVISIONED_KEY_NAMES array there would never be visible to the
# parent shell's cleanup trap. Track provisioned names in a file instead, which subshells can
# still append to.
PROVISIONED_NAMES_FILE="$(mktemp)"

pass() {
  PASS_COUNT=$((PASS_COUNT + 1))
  printf '  \033[32mPASS\033[0m  %s\n' "$1"
}

fail() {
  FAIL_COUNT=$((FAIL_COUNT + 1))
  FAILURE_MESSAGES+=("$1")
  printf '  \033[31mFAIL\033[0m  %s\n' "$1"
}

info() {
  printf '  ...   %s\n' "$1"
}

heading() {
  printf '\n\033[1m== %s ==\033[0m\n' "$1"
}

# assert_status_in DESC ACTUAL "200 201"
assert_status_in() {
  local desc="$1" actual="$2" allowed="$3"
  local s
  for s in $allowed; do
    if [[ "$actual" == "$s" ]]; then
      pass "$desc (got $actual)"
      return 0
    fi
  done
  fail "$desc (got $actual, expected one of: $allowed)"
  return 1
}

# assert_eq DESC EXPECTED ACTUAL
assert_eq() {
  local desc="$1" expected="$2" actual="$3"
  if [[ "$actual" == "$expected" ]]; then
    pass "$desc (got $actual)"
  else
    fail "$desc (expected '$expected', got '$actual')"
  fi
}

# assert_ge DESC ACTUAL MIN
assert_ge() {
  local desc="$1" actual="$2" min="$3"
  if [[ "$actual" -ge "$min" ]]; then
    pass "$desc ($actual >= $min)"
  else
    fail "$desc ($actual < $min)"
  fi
}

require_tools() {
  local missing=()
  local t
  for t in curl jq openssl docker; do
    command -v "$t" >/dev/null 2>&1 || missing+=("$t")
  done
  if [[ ${#missing[@]} -gt 0 ]]; then
    echo "Missing required tools: ${missing[*]}" >&2
    exit 1
  fi
}

# get_token USERNAME PASSWORD -> prints access_token
get_token() {
  local username="$1" password="$2"
  curl -s "$KEYCLOAK_URL/realms/$KEYCLOAK_REALM/protocol/openid-connect/token" \
    -d grant_type=password -d "client_id=$SMOKE_CLIENT_ID" -d "client_secret=$SMOKE_CLIENT_SECRET" \
    -d "username=$username" -d "password=$password" | jq -r '.access_token // empty'
}

# provision_api_key NAME_SUFFIX -> prints raw API key, tracks name for cleanup
provision_api_key() {
  local name="${RUN_ID}-$1"
  local raw_key="ak_$(openssl rand -base64 32 | tr '+/' '-_' | tr -d '=')"
  local key_hash
  key_hash=$(printf '%s' "$raw_key" | openssl dgst -sha256 -hmac "$API_KEY_PEPPER" | awk '{print $NF}')
  "${DC[@]}" exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
    "INSERT INTO gateway.api_client (name, key_hash) VALUES ('$name', '$key_hash');" >/dev/null
  echo "$name" >> "$PROVISIONED_NAMES_FILE"
  echo "$raw_key"
}

# http_status METHOD URL [HEADER...] -- writes body to $BODY_FILE, prints status code
BODY_FILE="$(mktemp)"
http_status() {
  local method="$1" url="$2"
  shift 2
  curl -s -o "$BODY_FILE" -w '%{http_code}' -X "$method" "$@" "$url"
}

body_json() {
  jq -r "$1" "$BODY_FILE" 2>/dev/null
}

wait_for_health() {
  local url="$1" timeout="${2:-60}"
  local waited=0
  while (( waited < timeout )); do
    if curl -s -o /dev/null -w '%{http_code}' "$url" 2>/dev/null | grep -q '^200$'; then
      return 0
    fi
    sleep 2
    waited=$((waited + 2))
  done
  return 1
}

cleanup() {
  if [[ ${PRICING_SERVICE_STOPPED} -eq 1 ]]; then
    info "cleanup: restarting pricing-service"
    "${DC[@]}" start pricing-service >/dev/null 2>&1 || true
  fi
  if [[ -s "$PROVISIONED_NAMES_FILE" ]]; then
    local names=() in_list name
    while IFS= read -r name; do
      names+=("$name")
    done < "$PROVISIONED_NAMES_FILE"
    in_list=$(printf "'%s'," "${names[@]}")
    in_list="${in_list%,}"
    "${DC[@]}" exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
      "DELETE FROM gateway.api_client WHERE name IN ($in_list);" >/dev/null 2>&1 || true
  fi
  rm -f "$BODY_FILE" "$PROVISIONED_NAMES_FILE"
}
trap cleanup EXIT
