# Acceptance criterion 3: reads (10/sec route, burst 20) sit well above the per-client cap
# (5/sec, burst 10), so a read burst demonstrates the client bucket specifically — and a second
# client's burst, run immediately after, proves the two clients' buckets don't share quota.
#
# Alice and bob are each tagged with their own simulated IP: without X-Forwarded-For, every
# request in this scenario would resolve to the same real peer address (the Docker bridge
# gateway, for a host-to-container curl), and their combined successful traffic can exceed the
# per-IP bucket's own cap — which would fail bob's requests for an unrelated reason (IP policy,
# not client policy) and defeat the point of this scenario.
scenario_3_client_bucket_independence() {
  heading "Criterion 3: user/client rate-limit bucket is independent per client"

  local alice_token bob_token key1 key2
  alice_token=$(get_token alice alice-dev-password)
  bob_token=$(get_token bob bob-dev-password)
  key1=$(provision_api_key c3-1)
  key2=$(provision_api_key c3-2)

  if [[ -z "$alice_token" || -z "$bob_token" ]]; then
    fail "obtained both alice and bob JWTs from Keycloak"
    return
  fi

  local success=0 limited=0 other=0 saw_client_code=1
  local i status code
  for i in $(seq 1 15); do
    status=$(http_status GET "$GATEWAY_URL/api/products" \
      -H "Authorization: Bearer $alice_token" -H "X-API-Key: $key1" \
      -H "X-Forwarded-For: 198.51.100.11")
    case "$status" in
      200) success=$((success + 1)) ;;
      429)
        limited=$((limited + 1))
        code=$(body_json '.code')
        [[ "$code" == "CLIENT_LIMIT_EXCEEDED" ]] && saw_client_code=0
        ;;
      *) other=$((other + 1)); info "unexpected status $status on alice attempt $i: $(cat "$BODY_FILE")" ;;
    esac
  done
  info "client 1 (alice): $success succeeded, $limited rate-limited, $other other"
  assert_ge "client 1's burst succeeded at least once" "$success" 1
  assert_ge "client 1's burst was rate-limited" "$limited" 1
  assert_eq "client 1's 429s carry code=CLIENT_LIMIT_EXCEEDED" 0 "$saw_client_code"

  local bob_success=0 bob_other=0
  for i in $(seq 1 15); do
    status=$(http_status GET "$GATEWAY_URL/api/products" \
      -H "Authorization: Bearer $bob_token" -H "X-API-Key: $key2" \
      -H "X-Forwarded-For: 198.51.100.12")
    if [[ "$status" == "200" ]]; then
      bob_success=$((bob_success + 1))
    else
      bob_other=$((bob_other + 1))
      info "unexpected status $status on bob attempt $i: $(cat "$BODY_FILE")"
    fi
  done
  info "client 2 (bob), run right after client 1 was limited: $bob_success succeeded, $bob_other other"
  assert_ge "client 2's burst succeeds while client 1 is still limited" "$bob_success" 10
}
