#!/usr/bin/env bash
#
# Automated smoke suite for the acceptance criteria in PLAN.md § "Manual Demonstration
# Acceptance Criteria", covering criteria 2 through 7 (see DEMO.md for the equivalent manual
# curl walkthrough, including criteria 1, 8, and 9 which this suite does not automate).
#
# Prerequisites: a full Compose deployment already running —
#   docker compose -f docker-compose.infra.yml -f docker-compose.yml up -d --build
# — with Keycloak's healthcheck passing. Run this script from anywhere; it locates the repo root
# relative to its own path.
#
# Config is read from the repo root .env (if present), then falls back to the same defaults as
# .env.example. Override any of GATEWAY_URL / API_SERVER_URL / PRICING_SERVICE_PORT /
# KEYCLOAK_URL / KEYCLOAK_REALM / SMOKE_CLIENT_SECRET / POSTGRES_USER / POSTGRES_DB /
# API_KEY_PEPPER via environment variables if your deployment differs.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck source=lib/common.sh
source "$SCRIPT_DIR/lib/common.sh"
# shellcheck source=scenarios/criterion_2_write_route_limit.sh
source "$SCRIPT_DIR/scenarios/criterion_2_write_route_limit.sh"
# shellcheck source=scenarios/criterion_3_client_bucket_independence.sh
source "$SCRIPT_DIR/scenarios/criterion_3_client_bucket_independence.sh"
# shellcheck source=scenarios/criterion_4_ip_bucket.sh
source "$SCRIPT_DIR/scenarios/criterion_4_ip_bucket.sh"
# shellcheck source=scenarios/criterion_5_circuit_breaker_opens.sh
source "$SCRIPT_DIR/scenarios/criterion_5_circuit_breaker_opens.sh"
# shellcheck source=scenarios/criterion_6_circuit_breaker_recovers.sh
source "$SCRIPT_DIR/scenarios/criterion_6_circuit_breaker_recovers.sh"
# shellcheck source=scenarios/criterion_7_pricing_outage_recovery.sh
source "$SCRIPT_DIR/scenarios/criterion_7_pricing_outage_recovery.sh"

require_tools

echo "Checking that the required services are reachable..."
if ! curl -s -o /dev/null "$KEYCLOAK_URL/realms/$KEYCLOAK_REALM/protocol/openid-connect/certs"; then
  echo "Keycloak is not reachable at $KEYCLOAK_URL — is the Compose deployment up?" >&2
  echo "  docker compose -f docker-compose.infra.yml -f docker-compose.yml up -d --build" >&2
  exit 1
fi
if ! curl -sf -o /dev/null "$GATEWAY_URL/actuator/health/readiness"; then
  echo "Gateway is not reachable/ready at $GATEWAY_URL — is the Compose deployment up?" >&2
  exit 1
fi
if ! curl -sf -o /dev/null "$API_SERVER_URL/actuator/health/readiness"; then
  echo "api-server is not reachable/ready at $API_SERVER_URL — is the Compose deployment up?" >&2
  exit 1
fi
echo "OK — running smoke suite (run id: $RUN_ID)"

scenario_2_write_route_limit
scenario_3_client_bucket_independence
scenario_4_ip_bucket
scenario_5_circuit_breaker_opens
scenario_6_circuit_breaker_recovers
scenario_7_pricing_outage_recovery

echo
echo "=========================================="
echo "  $PASS_COUNT passed, $FAIL_COUNT failed"
echo "=========================================="

if [[ $FAIL_COUNT -gt 0 ]]; then
  echo
  echo "Failures:"
  for msg in "${FAILURE_MESSAGES[@]}"; do
    echo "  - $msg"
  done
  exit 1
fi

exit 0
