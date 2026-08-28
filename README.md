# spring-gateway-sample

A Spring Boot / Spring Cloud Gateway demonstration monorepo: a public gateway (JWT + API-key
auth, Redis-backed per-route/per-client/per-IP rate limits) fronting an `api-server` that
delegates to `product-service` and `pricing-service`, with circuit breakers/cached fallbacks, a
Vue 3 traffic simulator, and Prometheus/Grafana/Tempo/Loki observability.

Targets JDK 25, Spring Boot 4.1.0, Spring Cloud 2025.1.2.

## Modules

| Module             | Description                                                                 |
|---------------------|------------------------------------------------------------------------------|
| `gateway`           | Public entry point (WebFlux). JWT + API-key auth, three-bucket Redis rate limiting, routing to `api-server`. |
| `api-server`        | Delegates 1:1 to `product-service`/`pricing-service` via `WebClient`, with Caffeine-cached GETs and Resilience4j circuit breakers. |
| `product-service`   | Product CRUD (Spring MVC, JPA, Flyway).                                     |
| `pricing-service`   | Pricing CRUD (Spring MVC, JPA, Flyway).                                     |
| `web-ui`            | Vue 3 + TypeScript traffic simulator — Keycloak PKCE login, API-key entry, single/burst request controls, live results. |
| `smoke-tests`       | Bash suite automating the manual acceptance criteria in `DEMO.md` against a running Compose deployment. |

All four Spring services share one Postgres container with schema-per-service (`product`,
`pricing`, `gateway`).

## Prerequisites

- JDK 25
- Maven
- Docker (this project uses [Colima](https://github.com/abiodun/colima) locally; any Docker
  daemon works)
- Node.js (for `web-ui`)
- `jq` (used by `DEMO.md` and `smoke-tests`)

## Getting started

```bash
cp .env.example .env
docker compose -f docker-compose.infra.yml -f docker-compose.yml up -d --build
```

`docker-compose.infra.yml` holds slow-moving backing services (Postgres, Redis, Keycloak,
Prometheus, Grafana, Tempo, Loki, Alloy); `docker-compose.yml` holds the frequently-rebuilt
Spring services + `web-ui`. Wait for Keycloak's healthcheck to pass (`docker compose ps`) before
using the stack — every service depends on it.

### Default ports

| Service            | Port   |
|---------------------|--------|
| gateway             | 8080   |
| api-server          | 8081   |
| product-service     | 8082   |
| pricing-service     | 8083   |
| web-ui              | 8090   |
| Postgres            | 5432   |
| Redis               | 6379   |
| Keycloak            | 8180   |
| Prometheus          | 9090   |
| Grafana             | 3000   |
| Tempo               | 3200   |
| Loki                | 3100   |
| Alloy               | 12345  |

Walk through `DEMO.md` for a full manual tour (auth, rate limiting, circuit breakers, tracing),
or run `smoke-tests/run.sh` to exercise the same acceptance criteria automatically.

## Building and testing

Run from the repo root:

```bash
mvn compile                          # build everything
mvn -pl <module> -am compile         # build one module + its dependencies
mvn test                             # run all tests in all modules
mvn -pl <module> -am test                                 # one module's tests
mvn -pl <module> -am test -Dtest=ClassName                # single test class
mvn -pl <module> -am test -Dtest=ClassName#methodName      # single test method
```

Integration tests use Testcontainers and need Docker running. Set `DOCKER_HOST` first if you're
on Colima:

```bash
colima list
export DOCKER_HOST="unix://$HOME/.colima/<profile>/docker.sock"
export TESTCONTAINERS_RYUK_DISABLED=true
```

### Running a service locally (outside Docker)

With the backing containers from `docker-compose.infra.yml` running:

```bash
SPRING_PROFILES_ACTIVE=local mvn -pl <module> -am spring-boot:run
```

### web-ui

```bash
cd web-ui
npm install
npm run dev       # Vite dev server
npm run build      # vue-tsc -b && vite build
```
