# Acceptance criterion 7: stopping pricing-service simulates a real downstream outage. A GET
# should serve the cached value with a degraded reason; a mutation has no cache to fall back to
# and must return 503. Restarting the container should restore normal behavior.
#
# Stops and restarts the pricing-service container — common.sh's cleanup trap restarts it too, so
# it comes back even if an assertion below fails.
scenario_7_pricing_outage_recovery() {
  heading "Criterion 7: pricing-service outage serves cached fallback, then recovers"

  local alice_token
  alice_token=$(get_token alice alice-dev-password)
  if [[ -z "$alice_token" ]]; then
    fail "obtained an alice JWT from Keycloak"
    return
  fi

  local status
  status=$(http_status GET "$API_SERVER_URL/api/prices/1" -H "Authorization: Bearer $alice_token")
  if [[ "$status" != "200" ]]; then
    fail "warm-up GET /api/prices/1 succeeded (got $status)"
    return
  fi
  local has_meta
  has_meta=$(body_json 'has("meta")')
  assert_eq "warm-up response is live (no meta envelope)" "false" "$has_meta"

  info "stopping pricing-service"
  "${DC[@]}" stop pricing-service >/dev/null
  PRICING_SERVICE_STOPPED=1

  # Give api-server's WebClient a moment to actually observe the connection failure.
  sleep 2

  status=$(http_status GET "$API_SERVER_URL/api/prices/1" -H "Authorization: Bearer $alice_token")
  local reason degraded source
  reason=$(body_json '.meta.reason')
  degraded=$(body_json '.meta.degraded')
  source=$(body_json '.meta.source')
  info "GET during outage: status=$status meta.reason=$reason"
  assert_status_in "GET during outage still returns 200 (degraded envelope)" "$status" "200"
  assert_eq "GET during outage is marked degraded" "true" "$degraded"
  assert_eq "GET during outage is served from cache" "cache" "$source"
  case "$reason" in
    PRICING_SERVICE_CALL_FAILED|PRICING_SERVICE_CIRCUIT_OPEN) pass "GET during outage carries a pricing-service failure reason (got $reason)" ;;
    *) fail "GET during outage carries a pricing-service failure reason (got $reason)" ;;
  esac

  status=$(http_status POST "$API_SERVER_URL/api/prices" -H "Authorization: Bearer $alice_token" \
    -H "Content-Type: application/json" \
    -d '{"productId":1,"amount":9.99,"currency":"USD"}')
  local code
  code=$(body_json '.code')
  info "POST during outage: status=$status code=$code"
  assert_eq "mutation during outage returns 503" "503" "$status"
  assert_eq "mutation during outage carries code=PRICING_SERVICE_UNAVAILABLE" "PRICING_SERVICE_UNAVAILABLE" "$code"

  info "restarting pricing-service"
  "${DC[@]}" start pricing-service >/dev/null
  PRICING_SERVICE_STOPPED=0

  if ! wait_for_health "http://localhost:${PRICING_SERVICE_PORT:-8083}/actuator/health/readiness" 60; then
    fail "pricing-service became healthy again within 60s"
    return
  fi
  pass "pricing-service became healthy again"

  # DEMO.md notes this itself ("retry both — they recover"): api-server's WebClient connection
  # pool can hold a pooled connection to the now-dead old container for one more failed attempt,
  # and if enough failures occurred during the outage to open the pricingService breaker, its
  # open-state wait duration (10s) must also elapse before a probe call is let through. Retry
  # rather than asserting on a single attempt.
  local recovered=1 i reason
  for i in $(seq 1 15); do
    sleep 1
    status=$(http_status GET "$API_SERVER_URL/api/prices/1" -H "Authorization: Bearer $alice_token")
    reason=$(body_json '.meta.reason // "live"')
    info "post-recovery GET attempt $i: status=$status meta.reason=$reason"
    if [[ "$status" == "200" && "$reason" == "live" ]]; then
      recovered=0
      break
    fi
  done
  assert_eq "GET recovers to a live (non-degraded) response within 15s of retries" 0 "$recovered"

  local mutation_recovered=1
  for i in $(seq 1 15); do
    status=$(http_status POST "$API_SERVER_URL/api/prices" -H "Authorization: Bearer $alice_token" \
      -H "Content-Type: application/json" \
      -d '{"productId":1,"amount":9.99,"currency":"USD"}')
    info "post-recovery POST attempt $i: status=$status"
    if [[ "$status" == "201" ]]; then
      mutation_recovered=0
      break
    fi
    sleep 1
  done
  assert_eq "mutation recovers to succeeding within 15s of retries" 0 "$mutation_recovered"
}
