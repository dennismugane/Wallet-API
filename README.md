# Muigo Wallet API

A production-grade digital wallet REST API built with Spring Boot 3, designed to demonstrate real-world DevOps and backend engineering practices.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│  GitHub Actions CI/CD                                           │
│  test → security-scan → build+push → deploy-staging → prod     │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│  Kubernetes (EKS / GKE)                                         │
│  ┌─────────────────┐    ┌──────────────────┐                   │
│  │  Ingress (HTTPS)│───▶│  Service (ClusterIP)│                │
│  └─────────────────┘    └────────┬─────────┘                   │
│                                  │                               │
│  ┌───────────────────────────────▼──────────────────────────┐  │
│  │  Deployment (2–10 replicas, HPA on CPU)                  │  │
│  │  Pod: muigo-wallet  + liveness/readiness probes          │  │
│  └──────────────────────────────────────────────────────────┘  │
└──────────────────────────────┬──────────────────────────────────┘
                               │
          ┌────────────────────┼───────────────────┐
          │                    │                   │
    ┌─────▼──────┐   ┌─────────▼──────┐   ┌──────▼──────────┐
    │ PostgreSQL │   │   Prometheus   │   │   Grafana        │
    │ (RDS/Multi │   │ (metrics scrape│   │ (dashboards)     │
    │    AZ)     │   │   /actuator)   │   │                  │
    └────────────┘   └────────────────┘   └──────────────────┘
```

---

## What Was Upgraded (and Why)

| Original Code | Upgraded Version | Why It Matters |
|---|---|---|
| `double balance` | `BigDecimal balance` | Floating-point cannot represent money exactly — `0.1 + 0.2 ≠ 0.3` in IEEE 754 |
| No `@Transactional` on transfer | `@Transactional` + pessimistic locking | Without this, two concurrent transfers can overdraw a wallet |
| `RuntimeException` for all errors | Typed exceptions + RFC 7807 Problem Detail | Callers can't programmatically handle errors without distinct types |
| No input validation | Bean Validation (`@NotBlank`, `@DecimalMin`) | Invalid requests reach business logic and cause cryptic errors |
| Returns raw `Wallet` entity | Response DTOs | Exposes JPA internals (version field, lazy-load proxies) to API consumers |
| No auth | JWT + Spring Security | Any caller can transfer money from any wallet |
| No transaction history | Append-only `Transaction` ledger | No audit trail — impossible to debug or investigate disputes |
| No tests | Unit tests + integration tests | Every change is a gamble without a test suite |
| No Dockerfile | Multi-stage, non-root, layered | Single-stage images are large and contain build tools in production |
| No CI/CD | GitHub Actions (5-stage pipeline) | Manual deployments don't scale and can't enforce quality gates |

---

## Tech Stack

- **Java 21** + **Spring Boot 3.2**
- **PostgreSQL 16** — NUMERIC(19,4) for monetary values
- **Flyway** — database schema migrations (version-controlled, repeatable)
- **Spring Security** + **JWT** — stateless authentication
- **Prometheus** + **Grafana** — metrics and dashboards
- **Docker** — multi-stage, non-root, layered image (~90MB)
- **Kubernetes** — deployment, HPA, liveness/readiness probes, ingress
- **GitHub Actions** — 5-stage CI/CD pipeline

---

## Running Locally

### With Docker Compose (recommended)

```bash
docker compose up -d
```

| Service    | URL                                    |
|------------|----------------------------------------|
| API        | http://localhost:8080                  |
| Swagger UI | http://localhost:8080/swagger-ui.html  |
| pgAdmin    | http://localhost:5050                  |
| Grafana    | http://localhost:3000                  |
| Prometheus | http://localhost:9090                  |

### With Maven (requires local Postgres)

```bash
# Start Postgres only
docker compose up postgres -d

# Run the app
./mvnw spring-boot:run
```

---

## API Quick Start

### 1. Register
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"password123","fullName":"Your Name"}'
```

### 2. Login — copy the token from the response
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"password123"}'
```

### 3. Create a wallet
```bash
TOKEN="<your-jwt-token>"
curl -X POST http://localhost:8080/api/v1/wallets \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"ownerName":"Your Name"}'
```

### 4. Deposit funds
```bash
curl -X POST http://localhost:8080/api/v1/wallets/deposit \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"walletId":"<wallet-id>","amount":1000.00}'
```

### 5. Transfer between wallets
```bash
curl -X POST http://localhost:8080/api/v1/wallets/transfer \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"fromWalletId":"<from-id>","toWalletId":"<to-id>","amount":250.00}'
```

---

## Running Tests

```bash
# Unit + integration tests
./mvnw test

# With coverage report (target/site/jacoco/index.html)
./mvnw verify
```

---

## Key Engineering Decisions

### Why `BigDecimal` instead of `double`?
`double` cannot represent most decimal fractions exactly. `0.1 + 0.2` evaluates to `0.30000000000000004`. In production this causes balances to drift. `BigDecimal` uses arbitrary-precision decimal arithmetic.

### Why Pessimistic Locking on transfers?
Without a lock, two concurrent transfers from the same wallet can both read the same balance, both pass the balance check, and both debit — resulting in a negative balance. The `PESSIMISTIC_WRITE` lock serialises concurrent writes to the same wallet row.

### Why are wallets locked in ID order?
If Thread A locks wallet-1 then wallet-2, and Thread B locks wallet-2 then wallet-1, they deadlock. Locking always in lexicographic order ensures both threads acquire locks in the same order, eliminating the circular wait.

### Why an append-only transaction log?
Every financial event is recorded as an immutable ledger entry. This enables: balance reconstruction, dispute investigation, fraud detection, and compliance auditing. Balances are derived from this log.

---

## CI/CD Pipeline

```
push/PR ──▶ [test] ──▶ [security-scan] ──▶ [build+push] ──▶ [deploy-staging] ──▶ [deploy-prod]
                │              │                                      │                   │
            JUnit 5        Semgrep SAST                        smoke test          manual approval
            Mockito        Trivy image scan                    + auto-rollback     + smoke test
                                                                                   + auto-rollback
```

The production deploy requires a manual approval in GitHub Environments. If the smoke test fails at any stage, the previous version is automatically restored with `kubectl rollout undo`.

---

## Project Structure

```
src/
├── main/java/com/muigo/wallet/
│   ├── WalletApplication.java
│   ├── config/          # Security, OpenAPI config
│   ├── controllers/     # REST endpoints
│   ├── dtos/            # Request/response DTOs (no entity leakage)
│   ├── exceptions/      # Typed exceptions + global handler
│   ├── models/          # JPA entities
│   ├── repositories/    # Spring Data JPA
│   ├── security/        # JWT filter + service
│   └── service/         # Business logic
├── main/resources/
│   ├── application.properties
│   └── db/migration/    # Flyway SQL migrations
└── test/                # Unit + integration tests

k8s/                     # Kubernetes manifests
docker/                  # Prometheus + Grafana config
.github/workflows/       # GitHub Actions CI/CD pipeline
Dockerfile               # Multi-stage, non-root build
docker-compose.yml       # Full local stack
```
