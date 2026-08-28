# Acceptance criterion 5: product-service's dev-only ?mode=slow delay (5s) exceeds api-server's
# Resilience4j time-limiter (3s), so repeated slow calls count as circuit-breaker failures. Once
# the sliding window (10 calls, minimum 5, 50% failure threshold) trips, the breaker opens and
# further calls short-circuit straight to the cached degraded response instead of waiting out the
# timeout again.
scenario_5_circuit_breaker_opens() {
  heading "Criterion 5: circuit breaker opens under ?mode=slow and serves cached fallback"

  local alice_token
  alice_token=$(get_token alice alice-dev-password)
  if [[ -z "$alice_token" ]]; then
    fail "obtained an alice JWT from Keycloak"
    return
  fi

  # Warm the Caffeine cache with a normal call so a fallback value exists once the breaker trips.
  local status
  status=$(http_status GET "$API_SERVER_URL/api/products/1" -H "Authorization: Bearer $alice_token")
  if [[ "$status" != "200" ]]; then
    fail "warm-up GET /api/products/1 succeeded (got $status)"
    return
  fi
  local has_meta
  has_meta=$(body_json 'has("meta")')
  assert_eq "warm-up response is live (no meta envelope)" "false" "$has_meta"

  local saw_circuit_open=1 i reason degraded source
  for i in $(seq 1 10); do
    status=$(http_status GET "$API_SERVER_URL/api/products/1?mode=slow" -H "Authorization: Bearer $alice_token")
    reason=$(body_json '.meta.reason')
    degraded=$(body_json '.meta.degraded')
    source=$(body_json '.meta.source')
    info "attempt $i: status=$status meta.reason=$reason meta.degraded=$degraded"
    if [[ "$reason" == "PRODUCT_SERVICE_CIRCUIT_OPEN" ]]; then
      saw_circuit_open=0
      assert_eq "circuit-open response is marked degraded" "true" "$degraded"
      assert_eq "circuit-open response is served from cache" "cache" "$source"
      break
    fi
  done

  assert_eq "circuit opened and served a degraded cached response within 10 slow calls" 0 "$saw_circuit_open"
}
