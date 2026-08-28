# Acceptance criterion 2: a write burst hits the route bucket (2/sec, burst 5) before the
# per-user bucket (5/sec, burst 10) and returns a structured 429.
#
# Tagged with its own simulated IP so its successful writes don't consume shared quota in the
# untagged/real-IP bucket that other scenarios (or repeat runs) might also draw from.
scenario_2_write_route_limit() {
  heading "Criterion 2: write burst trips the route rate limit"

  local token key
  token=$(get_token alice alice-dev-password)
  key=$(provision_api_key c2)

  if [[ -z "$token" ]]; then
    fail "obtained an alice JWT from Keycloak"
    return
  fi

  local success=0 limited=0 other=0 saw_route_code=1 saw_retry_after=1
  local i status code headers_file
  headers_file="$(mktemp)"
  for i in $(seq 1 8); do
    status=$(curl -s -o "$BODY_FILE" -D "$headers_file" -w '%{http_code}' -X POST \
      "$GATEWAY_URL/api/products" \
      -H "Authorization: Bearer $token" -H "X-API-Key: $key" \
      -H "X-Forwarded-For: 198.51.100.10" \
      -H "Content-Type: application/json" \
      -d "{\"sku\":\"${RUN_ID}-c2-$i\",\"name\":\"Smoke Demo\",\"description\":\"d\"}")
    case "$status" in
      201) success=$((success + 1)) ;;
      429)
        limited=$((limited + 1))
        code=$(body_json '.code')
        [[ "$code" == "ROUTE_LIMIT_EXCEEDED" ]] && saw_route_code=0
        grep -qi '^Retry-After:' "$headers_file" && saw_retry_after=0
        ;;
      *) other=$((other + 1)); info "unexpected status $status on attempt $i: $(cat "$BODY_FILE")" ;;
    esac
  done
  rm -f "$headers_file"

  info "results: $success succeeded, $limited rate-limited, $other other"
  assert_ge "at least one write succeeded" "$success" 1
  assert_ge "at least one write was rate-limited" "$limited" 1
  assert_eq "429 responses carry code=ROUTE_LIMIT_EXCEEDED" 0 "$saw_route_code"
  assert_eq "429 responses carry a Retry-After header" 0 "$saw_retry_after"
  assert_eq "no unexpected status codes" 0 "$other"
}
