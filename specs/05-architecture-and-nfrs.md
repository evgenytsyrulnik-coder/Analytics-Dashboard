# Architecture & Non-Functional Requirements

## 1. System Architecture

```
                                ┌─────────────────────────────┐
                                │       Frontend (SPA)        │
                                │    React + TypeScript        │
                                │    Served via CDN / S3       │
                                └──────────┬──────────────────┘
                                           │ HTTPS
                                           ▼
                                ┌─────────────────────────────┐
                                │      API Gateway / LB       │
                                │    (AWS ALB or Kong)         │
                                │    Rate limiting, TLS        │
                                └──────────┬──────────────────┘
                                           │
                          ┌────────────────┼────────────────┐
                          ▼                ▼                ▼
                 ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
                 │  Analytics  │  │  Analytics  │  │  Analytics  │
                 │  Service    │  │  Service    │  │  Service    │
                 │  (replica)  │  │  (replica)  │  │  (replica)  │
                 └──────┬──────┘  └──────┬──────┘  └──────┬──────┘
                        │ Java 21 / Spring Boot 3          │
                        ├──────────────────────────────────┤
                        │                                  │
              ┌─────────▼─────────┐             ┌─────────▼─────────┐
              │    PostgreSQL     │             │    ClickHouse      │
              │   (Reference +   │             │   (Analytics       │
              │    Run Details)   │             │    OLAP)           │
              └──────────────────┘             └────────────────────┘
                        │
              ┌─────────▼─────────┐
              │      Redis        │
              │   (Cache layer)   │
              └──────────────────┘

                                      ┌─────────────────────────┐
                                      │     Kafka               │
  Agent Platform ────────────────────►│  topic: agent-run-events│
                                      └──────────┬──────────────┘
                                                 │
                                      ┌──────────▼──────────────┐
                                      │   Ingestion Service     │
                                      │   (Spring Kafka         │
                                      │    Consumer)            │
                                      │                         │
                                      │  Writes to:             │
                                      │   • PostgreSQL          │
                                      │   • ClickHouse          │
                                      └─────────────────────────┘

                                      ┌─────────────────────────┐
                                      │   Budget Checker        │
                                      │   (Scheduled Job)       │
                                      │   Runs every 5 min      │
                                      │                         │
                                      │   Reads ClickHouse →    │
                                      │   Checks thresholds →   │
                                      │   Sends notifications   │
                                      └─────────────────────────┘

                                      ┌─────────────────────────┐
                                      │   Export Worker         │
                                      │   (Async, queue-based)  │
                                      │   Reads ClickHouse →    │
                                      │   Writes CSV to S3 →    │
                                      │   Updates exports table │
                                      └─────────────────────────┘
```

## 2. Service Decomposition

The backend is structured as a modular monolith (single deployable Spring Boot application with internal module boundaries). This avoids premature microservice complexity while keeping clean domain separation.

### Modules (Java packages)

```
com.analytics.dashboard
├── config/               # Spring configuration, security, ClickHouse datasource
├── auth/                 # JWT parsing, role extraction, authorization checks
├── org/                  # Organization, Team, User reference data
│   ├── controller/
│   ├── service/
│   └── repository/
├── analytics/            # Core analytics query logic
│   ├── controller/       # REST endpoints for summary, timeseries, breakdowns
│   ├── service/          # Business logic, query building
│   ├── repository/       # ClickHouse query execution
│   └── dto/              # Request/response DTOs
├── runs/                 # Agent run detail lookups
│   ├── controller/
│   ├── service/
│   └── repository/       # PostgreSQL queries for run detail
├── budget/               # Budget CRUD + threshold checking
│   ├── controller/
│   ├── service/
│   ├── repository/
│   └── scheduler/        # Scheduled budget threshold checker
├── export/               # CSV export orchestration
│   ├── controller/
│   ├── service/
│   └── worker/           # Async export job
├── ingestion/            # Kafka consumer for agent run events
│   ├── consumer/
│   └── writer/           # Dual-write to PG + ClickHouse
└── common/               # Shared utilities, error handling, pagination
    ├── exception/
    ├── pagination/
    └── dto/
```

## 3. Non-Functional Requirements

### 3.1 Performance

| Requirement | Target |
|---|---|
| API response time (p50) | < 200ms |
| API response time (p95) | < 500ms |
| API response time (p99) | < 2s |
| Dashboard page load (p95) | < 2s (including API calls) |
| Time-series query (90-day range, daily granularity) | < 300ms |
| CSV export (up to 100k rows) | < 30s end-to-end |
| Kafka ingestion lag | < 30s (event time to queryable in ClickHouse) |

### 3.2 Scalability

| Dimension | Capacity |
|---|---|
| Organizations | 10,000+ |
| Users per organization | Up to 10,000 |
| Agent runs per day (platform-wide) | 50 million |
| Agent runs per org per day (large org) | 500,000 |
| Concurrent dashboard users | 5,000 |
| Data retention (raw events) | 2 years |

### 3.3 Availability

| Requirement | Target |
|---|---|
| Uptime SLA | 99.9% (43.8 min downtime/month) |
| RTO (Recovery Time Objective) | < 15 minutes |
| RPO (Recovery Point Objective) | < 1 minute (Kafka replay) |
| Deployment strategy | Rolling (zero-downtime) via Kubernetes |

### 3.4 Security

| Requirement | Details |
|---|---|
| Transport | TLS 1.3 everywhere |
| Authentication | OAuth 2.0 JWT (RS256) validated against IdP JWKS |
| Authorization | Role-based (ORG_ADMIN, TEAM_LEAD, MEMBER) enforced at service layer |
| Tenant isolation | All queries scoped by `org_id`; no cross-org data leakage |
| PII | User email and display name are PII; stored encrypted at rest (AES-256) |
| Audit logging | All budget mutations and export requests are audit-logged |
| Secrets management | All credentials via Kubernetes Secrets or AWS Secrets Manager |
| OWASP | Input validation on all parameters; parameterized queries; no SQL concatenation |

### 3.5 Observability

| Concern | Tool |
|---|---|
| Structured logging | SLF4J + Logback, JSON format, correlated with trace IDs |
| Metrics | Micrometer → Prometheus → Grafana |
| Distributed tracing | OpenTelemetry → Jaeger |
| Alerting | PagerDuty integration via Grafana alerting rules |
| Health checks | Spring Actuator `/health`, `/readiness`, `/liveness` |

**Key metrics to instrument:**

- `analytics.api.request.duration` (histogram, tagged by endpoint, status)
- `analytics.api.request.count` (counter, tagged by endpoint, status)
- `analytics.clickhouse.query.duration` (histogram, tagged by query type)
- `analytics.ingestion.events.processed` (counter)
- `analytics.ingestion.lag.seconds` (gauge)
- `analytics.export.duration` (histogram)
- `analytics.cache.hit_rate` (gauge, tagged by cache name)

### 3.6 Caching Strategy

| Data | Cache Location | TTL | Invalidation |
|---|---|---|---|
| Analytics summary / timeseries | Redis | 5 minutes | Time-based expiry |
| Team and agent type reference data | Redis | 1 hour | Event-driven (on change) |
| User profile (from JWT) | In-memory (Caffeine) | 15 minutes | Token expiry |
| Budget current spend | Redis | 1 minute | Updated by budget checker job |

Cache keys follow the pattern: `analytics:{org_id}:{endpoint}:{hash_of_query_params}`.

### 3.7 Testing Strategy

| Level | Framework | Coverage Target |
|---|---|---|
| Unit tests | JUnit 5 + Mockito | ≥ 80% line coverage on service layer |
| Integration tests | Spring Boot Test + Testcontainers (PG, ClickHouse, Redis, Kafka) | All repository classes, all API endpoints |
| Contract tests | Spring Cloud Contract or Pact | API contract between frontend and backend |
| Load tests | Gatling (Java DSL) | Verify p95/p99 latency targets under expected concurrency |
| Frontend unit tests | Vitest + React Testing Library | ≥ 70% coverage |
| E2E tests | Playwright | Critical user journeys (org dashboard, drill-down, export) |

### 3.8 CI/CD Pipeline

```
Push to branch
  → Compile + Unit Tests (Gradle)
  → Integration Tests (Testcontainers)
  → Static Analysis (SpotBugs, Checkstyle)
  → Container Build (Docker)
  → Push to Container Registry
  → Deploy to Staging (Helm + ArgoCD)
  → Run E2E tests against staging
  → Manual approval gate
  → Deploy to Production (canary → full rollout)
```

## 4. Infrastructure

| Component | Specification |
|---|---|
| Kubernetes cluster | 3 nodes minimum (auto-scaling) |
| Analytics Service | 3 replicas, 2 CPU / 4 GB RAM each |
| Ingestion Service | 3 replicas (matching Kafka partition count) |
| PostgreSQL | AWS RDS, db.r6g.xlarge, Multi-AZ, 500 GB gp3 |
| ClickHouse | 3-node cluster (ReplicatedMergeTree), 8 CPU / 32 GB RAM each, 2 TB NVMe |
| Redis | AWS ElastiCache, r6g.large, cluster mode, 2 replicas |
| Kafka | AWS MSK, 3 brokers, m5.large, 1 TB per broker |
| S3 | Exports bucket, lifecycle policy for auto-deletion |

## 5. Disaster Recovery

- **PostgreSQL**: Automated daily snapshots + continuous WAL archiving to S3. Point-in-time recovery.
- **ClickHouse**: ReplicatedMergeTree across 3 nodes. Can rebuild from Kafka replay if needed.
- **Kafka**: 3x replication factor, 7-day retention. Serves as the system of record for event replay.
- **Redis**: Treated as ephemeral cache. Cold start takes < 5 minutes (cache misses hit DB directly).

## 6. Migration & Rollout Plan

### Phase 1: Foundation
- Deploy PostgreSQL, ClickHouse, Redis, Kafka infrastructure.
- Implement ingestion pipeline (Kafka consumer → dual write).
- Build and deploy analytics API (read-only endpoints).
- Internal dogfooding with synthetic data.

### Phase 2: Core Dashboard
- Build frontend: org dashboard, team dashboard, personal dashboard, run detail.
- Implement caching layer.
- Load testing and performance tuning.
- Beta release to 5 pilot customers.

### Phase 3: Budgets & Export
- Implement budget CRUD and threshold checker.
- Implement CSV export pipeline.
- Notification integration (in-app + email).
- GA release.

### Phase 4: Optimization
- Add materialized view optimizations based on observed query patterns.
- Implement predictive budget alerts ("At current rate, you will exceed budget by Jan 25").
- User feedback incorporation.
