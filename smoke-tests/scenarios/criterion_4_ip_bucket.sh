# Acceptance criterion 4: with the gateway's local-profile trusted-proxy config (trusts
# X-Forwarded-For from 127.0.0.1/::1 — exactly what a host-to-container curl presents), requests
# tagged with one simulated IP exhaust that IP's bucket (8/sec, burst 15) while a second,
# lightly-used simulated IP's own bucket stays untouched.
#
# The IP bucket sits strictly between the per-client bucket (5/sec, burst 10) and the route
# bucket (10/sec, burst 20): a single client can never trip it alone (its own client bucket binds
# first), and the route bucket (shared by every client/IP on this route) has enough headroom that
# a modest handful of clients concentrated on one simulated IP clear it before either the route
# cap or their own individual client caps bind — proving the IP bucket is what actually rejects.
#
# This scenario shares the products-read route bucket with criterion 3, which runs right before
# it and drains that same bucket — X-Forwarded-For tagging doesn't isolate the route bucket, only
# the client/IP ones. A short wait lets it refill (burst 20, refill 10/sec) before this scenario
# needs its own headroom there.
scenario_4_ip_bucket() {
  heading "Criterion 4: per-IP rate-limit bucket via simulated X-Forwarded-For"

  local token
  token=$(get_token alice alice-dev-password)
  if [[ -z "$token" ]]; then
    fail "obtained an alice JWT from Keycloak"
    return
  fi

  info "waiting 3s for the shared products-read route bucket to refill"
  sleep 3

  local num_keys=3
  local keys=()
  local k
  for k in $(seq 1 "$num_keys"); do
    keys+=("$(provision_api_key "c4-$k")")
  done

  local heavy_ip="203.0.113.1" light_ip="203.0.113.2"
  local heavy_success=0 heavy_ip_limited=0 light_success=0 light_ip_limited=0
  local total=24
  local i status code fwd key_idx

  for i in $(seq 1 "$total"); do
    key_idx=$(( (i - 1) % num_keys ))
    if (( i % 6 == 0 )); then
      fwd="$light_ip"
    else
      fwd="$heavy_ip"
    fi
    status=$(http_status GET "$GATEWAY_URL/api/products" \
      -H "Authorization: Bearer $token" -H "X-API-Key: ${keys[$key_idx]}" \
      -H "X-Forwarded-For: $fwd")
    code=""
    [[ "$status" == "429" ]] && code=$(body_json '.code')
    if [[ "$fwd" == "$heavy_ip" ]]; then
      [[ "$status" == "200" ]] && heavy_success=$((heavy_success + 1))
      [[ "$code" == "IP_LIMIT_EXCEEDED" ]] && heavy_ip_limited=$((heavy_ip_limited + 1))
    else
      [[ "$status" == "200" ]] && light_success=$((light_success + 1))
      [[ "$code" == "IP_LIMIT_EXCEEDED" ]] && light_ip_limited=$((light_ip_limited + 1))
    fi
  done

  info "heavy IP ($heavy_ip): $heavy_success succeeded, $heavy_ip_limited rejected by its IP bucket"
  info "light IP ($light_ip): $light_success succeeded, $light_ip_limited rejected by its IP bucket"
  assert_ge "heavy IP's burst succeeded at least once" "$heavy_success" 1
  assert_ge "heavy IP's bucket rejected at least one request as IP_LIMIT_EXCEEDED" "$heavy_ip_limited" 1
  assert_ge "light IP's burst succeeded at least once" "$light_success" 1
  assert_eq "light IP's bucket was never the reason for a rejection" 0 "$light_ip_limited"
}
