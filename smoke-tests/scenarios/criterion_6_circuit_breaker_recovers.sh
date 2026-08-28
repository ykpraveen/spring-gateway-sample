# Acceptance criterion 6: after the open-state wait duration (10s) elapses, the breaker moves to
# HALF_OPEN and permits probe calls; since these calls use normal mode (no ?mode=slow), they
# succeed and the breaker closes again, so responses stop carrying the degraded meta envelope.
#
# Depends on criterion 5 having just opened the productService breaker in this same run.
scenario_6_circuit_breaker_recovers() {
  heading "Criterion 6: circuit breaker half-opens and recovers"

  local alice_token
  alice_token=$(get_token alice alice-dev-password)
  if [[ -z "$alice_token" ]]; then
    fail "obtained an alice JWT from Keycloak"
    return
  fi

  info "waiting 11s for the open-state wait duration (10s) to elapse"
  sleep 11

  local saw_live=1 status reason i
  for i in $(seq 1 5); do
    status=$(http_status GET "$API_SERVER_URL/api/products/1" -H "Authorization: Bearer $alice_token")
    reason=$(body_json '.meta.reason // "live"')
    info "probe $i: status=$status meta.reason=$reason"
    if [[ "$status" == "200" && "$reason" == "live" ]]; then
      saw_live=0
      break
    fi
    sleep 1
  done

  assert_eq "breaker recovered: a probe call returned a live (non-degraded) response" 0 "$saw_live"
}
