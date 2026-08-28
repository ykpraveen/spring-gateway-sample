# Manual Demonstration Script

Walks through the acceptance criteria in `PLAN.md` § "Manual Demonstration Acceptance Criteria"
using `curl` against a full Compose deployment. Run everything from the repo root with `.env`
present (`cp .env.example .env` if you haven't already).

```
docker compose -f docker-compose.infra.yml -f docker-compose.yml up -d --build
```

Wait for Keycloak's healthcheck to pass (`docker compose ps`) before continuing — every service
depends on it.

Throughout, `jq` is used to pull fields out of JSON responses; install it or substitute your own
parsing.

## 0. Get a JWT and provision a demo API key

The `smoke-tests` Keycloak client (confidential, direct-grant-enabled — see
`infrastructure/keycloak/realm-export.json`) issues tokens for `alice` (full access:
`catalog.read/write`, `pricing.read/write`) and `bob` (read-only: `catalog.read`, `pricing.read`)
without a browser:

```bash
ALICE_TOKEN=$(curl -s http://localhost:8180/realms/gateway-sample/protocol/openid-connect/token \
  -d grant_type=password -d client_id=smoke-tests -d client_secret=dev-only-smoke-tests-secret \
  -d username=alice -d password=alice-dev-password | jq -r .access_token)

BOB_TOKEN=$(curl -s http://localhost:8180/realms/gateway-sample/protocol/openid-connect/token \
  -d grant_type=password -d client_id=smoke-tests -d client_secret=dev-only-smoke-tests-secret \
  -d username=bob -d password=bob-dev-password | jq -r .access_token)
```

There's no HTTP endpoint yet for API-key provisioning (`ApiKeyProvisioningService` is currently
only exercised from integration tests) — the traffic simulator expects a key to already exist.
Provision two demo clients directly, replicating `ApiKeyHasher`'s HMAC-SHA256-with-pepper digest
via `openssl` so gateway validation matches:

```bash
provision_api_key() {
  local name="$1" raw_key="ak_$(openssl rand -base64 32 | tr '+/' '-_' | tr -d '=')"
  local key_hash
  key_hash=$(printf '%s' "$raw_key" \
    | openssl dgst -sha256 -hmac dev-only-api-key-pepper-change-me | awk '{print $NF}')
  docker compose exec -T postgres psql -U gatewaysample -d gatewaysample -c \
    "INSERT INTO gateway.api_client (name, key_hash) VALUES ('$name', '$key_hash');" >/dev/null
  echo "$raw_key"
}

API_KEY_1=$(provision_api_key demo-client-1)
API_KEY_2=$(provision_api_key demo-client-2)
```

(`API_KEY_PEPPER` above must match `.env`'s value — the default shown is what `.env.example` ships.)

## 1. Sign in and read products through the gateway

```bash
curl -s http://localhost:8080/api/products \
  -H "Authorization: Bearer $ALICE_TOKEN" -H "X-API-Key: $API_KEY_1" | jq
```

Expect `200` with the seeded product catalog. In the web-ui (`http://localhost:8090` or `npm run
dev` at `:5173`), the same flow is the Keycloak PKCE login button + pasting `$API_KEY_1` into the
API-key field.

## 2. Trigger the write rate limit and read its `429` explanation

The write bucket (2/sec, burst 5) is deliberately below the per-user bucket (5/sec, burst 10), so a
write burst hits the route cap first. Each step below tags its own simulated `X-Forwarded-For` IP
so its successful requests don't quietly draw down the shared real-IP bucket other steps rely on
(step 4 needs a full one):

```bash
for i in $(seq 1 8); do
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/products \
    -X POST -H "Authorization: Bearer $ALICE_TOKEN" -H "X-API-Key: $API_KEY_1" \
    -H "X-Forwarded-For: 198.51.100.10" \
    -H "Content-Type: application/json" \
    -d '{"sku":"demo-'"$i"'","name":"Demo","description":"d"}'
done
```

Expect the first ~5 to succeed (`201`) and the rest to return `429` with a Problem Details body:
`code: ROUTE_LIMIT_EXCEEDED`. In the web-ui, the same burst via the "burst" control surfaces the
dedicated 429 explainer panel naming the exhausted bucket.

## 3. Show the user/client bucket is independent of the route bucket

Reads (10/sec route, 20 burst) are well above the per-client cap (5/sec, burst 10), so a read burst
demonstrates the *client* bucket specifically:

```bash
for i in $(seq 1 15); do
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/products \
    -H "Authorization: Bearer $ALICE_TOKEN" -H "X-API-Key: $API_KEY_1" \
    -H "X-Forwarded-For: 198.51.100.11"
done
```

Expect ~10 `200`s then `429 CLIENT_LIMIT_EXCEEDED`. Repeat immediately with `$BOB_TOKEN` +
`$API_KEY_2` + `X-Forwarded-For: 198.51.100.12` in place of `$ALICE_TOKEN`/`$API_KEY_1`/
`198.51.100.11` — it succeeds, proving the two clients' buckets don't share quota. (Bob needs his
own simulated IP here too — otherwise his requests would resolve to the same real IP as Alice's
just-exhausted traffic and could fail with `429 IP_LIMIT_EXCEEDED` instead, for an unrelated
reason.)

## 4. Demonstrate the per-IP bucket with simulated `X-Forwarded-For`

The gateway's `local` profile trusts `X-Forwarded-For` from `127.0.0.1`/`::1` *and* the Compose
network's CIDR (see `app.rate-limit.trusted-proxies` in `application-local.yml`) — the latter is
what actually matters here, since a host-to-container `curl` against the Dockerized deployment
arrives with a remote address of the bridge network's gateway IP, not loopback (Docker's userland
proxy rewrites the source address on the way in). That CIDR is pinned via
`gateway-sample-net.ipam.config` in `docker-compose.yml`/`docker-compose.infra.yml` specifically
so it stays valid — trusting only `127.0.0.1`/`::1` (right for running the gateway directly on the
host) silently drops every `X-Forwarded-For` header against this Compose deployment instead,
collapsing every simulated IP onto one real bucket.

The IP bucket (8/sec, burst 15) sits strictly between the per-client bucket (5/sec, burst 10) and
the route bucket (10/sec, burst 20): one client alone can never trip it — its own per-client cap
binds first — so demonstrating it needs **two** clients sharing one simulated IP, whose combined
traffic clears both per-client caps without ever clearing the shared route cap:

```bash
for i in $(seq 1 22); do
  key=$([ $((i % 2)) -eq 0 ] && echo "$API_KEY_1" || echo "$API_KEY_2")
  ip=$([ $((i % 6)) -eq 0 ] && echo "203.0.113.2" || echo "203.0.113.1")
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/products \
    -H "Authorization: Bearer $ALICE_TOKEN" -H "X-API-Key: $key" \
    -H "X-Forwarded-For: $ip"
done
```

Requests tagged `.1` alternate between `$API_KEY_1`/`$API_KEY_2` — each client's own bucket has
room to spare — but together they share one IP bucket and start returning
`429 IP_LIMIT_EXCEEDED` partway through. The occasional `.2` request lands in a separate IP bucket
and keeps succeeding throughout.

## 5. Open the circuit breaker with `mode=slow`

`product-service`'s dev-only `slow-delay` (5s) exceeds api-server's Resilience4j time-limiter
timeout (3s), so repeated slow calls count as failures:

```bash
for i in $(seq 1 8); do
  curl -s http://localhost:8081/api/products/1?mode=slow \
    -H "Authorization: Bearer $ALICE_TOKEN" | jq '.meta // "live"'
done
```

With `minimum-number-of-calls: 5` and `failure-rate-threshold: 50`, the circuit opens within the
first sliding window (10 calls). Once open, responses immediately return the last cached value as
`{"data": {...}, "meta": {"reason": "PRODUCT_SERVICE_CIRCUIT_OPEN", ...}}` — no more waiting on the
timeout. Watch `resilience4j_circuitbreaker_state{name="productService"}` flip from `CLOSED` to
`OPEN` on the **Gateway Sample — Overview** Grafana dashboard's Circuit Breakers row.

## 6. Watch it recover

```bash
sleep 11   # wait-duration-in-open-state
for i in $(seq 1 5); do
  curl -s http://localhost:8081/api/products/1 -H "Authorization: Bearer $ALICE_TOKEN" \
    | jq '.meta // "live"'
  sleep 1
done
```

The breaker moves to `HALF_OPEN` (3 permitted probe calls), and — since `mode=slow` isn't set this
time — those probes succeed, closing it again. The dashboard's state panel shows `CLOSED` →
`HALF_OPEN` → `CLOSED`.

## 7. Stop and restart pricing-service

```bash
docker compose stop pricing-service
curl -s http://localhost:8081/api/prices/1 -H "Authorization: Bearer $ALICE_TOKEN" | jq
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8081/api/prices \
  -X POST -H "Authorization: Bearer $ALICE_TOKEN" -H "Content-Type: application/json" \
  -d '{"productId":1,"amount":9.99,"currency":"USD"}'
docker compose start pricing-service
```

Expect the `GET` to serve a degraded cached response (`meta.reason: PRICING_SERVICE_CALL_FAILED`,
once cached data exists) and the `POST` to return `503` (mutations are never cached/degraded). After
`start`, retry both — they recover once the container is healthy again.

## 8. Inspect metrics and traces in Grafana

Open `http://localhost:3000` (`admin` / `$GRAFANA_ADMIN_PASSWORD`), folder **Gateway Sample** →
**Gateway Sample — Overview**:

- **Traffic** row: request rate / error rate / p95 latency per service, sourced from
  `http_server_requests_seconds_*` scraped off each service's `/actuator/prometheus`.
- **Gateway Rate Limits** row: allow/reject counts by bucket (route/client/ip) from the
  `gateway_rate_limit_requests_total` counter added in `RateLimitGlobalFilter` — corroborates
  steps 2-4 above.
- **Circuit Breakers** row: `resilience4j_circuitbreaker_state` / `_calls_seconds_count` —
  corroborates steps 5-6.

For distributed traces: open Grafana **Explore**, pick the **Tempo** datasource, and search recent
traces for `api-server` — each spans out to `product-service`/`pricing-service`. Click a span's
**Logs for this span** link to jump to the matching Loki log lines (matched on `trace_id`), or the
reverse — open **Explore** → **Loki**, run `{job="docker"} | json`, and click a log line's
`TraceID` derived-field link to jump back into Tempo. This round-trip is what
`infrastructure/grafana/provisioning/datasources/datasources.yml`'s `derivedFields` /
`tracesToLogsV2` wiring provides, fed by Alloy's Docker-log discovery
(`infrastructure/alloy/config.alloy`) and each service's `logging.structured.format.console:
logstash` JSON output.
