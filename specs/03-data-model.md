# Data Model Specification

## 1. Overview

The system uses two data stores:

1. **PostgreSQL** — Source of truth for transactional/reference data: organizations, teams, users, budgets, exports.
2. **ClickHouse** — OLAP store for agent run events. All analytical queries (summaries, timeseries, breakdowns) run against ClickHouse for sub-second response times at scale.

Agent run events are ingested into both stores: PostgreSQL holds a normalized record for detail lookups; ClickHouse holds a denormalized, pre-aggregated copy optimized for analytical queries.

## 2. PostgreSQL Schema

### 2.1 `organizations`

```sql
CREATE TABLE organizations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_id     VARCHAR(255) NOT NULL UNIQUE,  -- ID from platform IdP
    name            VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_organizations_external_id ON organizations(external_id);
```

### 2.2 `teams`

```sql
CREATE TABLE teams (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          UUID NOT NULL REFERENCES organizations(id),
    external_id     VARCHAR(255) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(org_id, external_id)
);

CREATE INDEX idx_teams_org_id ON teams(org_id);
```

### 2.3 `users`

```sql
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          UUID NOT NULL REFERENCES organizations(id),
    external_id     VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    display_name    VARCHAR(255) NOT NULL,
    role            VARCHAR(50) NOT NULL DEFAULT 'MEMBER',
                    -- CHECK (role IN ('ORG_ADMIN', 'TEAM_LEAD', 'MEMBER'))
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(org_id, external_id)
);

CREATE INDEX idx_users_org_id ON users(org_id);
CREATE INDEX idx_users_email ON users(email);
```

### 2.4 `user_teams` (many-to-many)

```sql
CREATE TABLE user_teams (
    user_id         UUID NOT NULL REFERENCES users(id),
    team_id         UUID NOT NULL REFERENCES teams(id),
    PRIMARY KEY (user_id, team_id)
);

CREATE INDEX idx_user_teams_team_id ON user_teams(team_id);
```

### 2.5 `agent_types`

```sql
CREATE TABLE agent_types (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          UUID NOT NULL REFERENCES organizations(id),
    slug            VARCHAR(100) NOT NULL,          -- e.g. "code_review"
    display_name    VARCHAR(255) NOT NULL,          -- e.g. "Code Review Agent"
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(org_id, slug)
);

CREATE INDEX idx_agent_types_org_id ON agent_types(org_id);
```

### 2.6 `agent_runs`

This is the PostgreSQL copy used for detail lookups (`GET /api/v1/runs/{runId}`).

```sql
CREATE TABLE agent_runs (
    id              UUID PRIMARY KEY,               -- assigned by the agent platform
    org_id          UUID NOT NULL REFERENCES organizations(id),
    team_id         UUID REFERENCES teams(id),
    user_id         UUID NOT NULL REFERENCES users(id),
    agent_type_slug VARCHAR(100) NOT NULL,
    model_name      VARCHAR(100),
    model_version   VARCHAR(50),
    status          VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
                    -- CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED'))
    started_at      TIMESTAMPTZ NOT NULL,
    finished_at     TIMESTAMPTZ,
    duration_ms     BIGINT,
    input_tokens    BIGINT NOT NULL DEFAULT 0,
    output_tokens   BIGINT NOT NULL DEFAULT 0,
    total_tokens    BIGINT NOT NULL DEFAULT 0,
    input_cost      NUMERIC(18,6) NOT NULL DEFAULT 0,
    output_cost     NUMERIC(18,6) NOT NULL DEFAULT 0,
    total_cost      NUMERIC(18,6) NOT NULL DEFAULT 0,
    error_category  VARCHAR(100),
    error_message   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_agent_runs_org_id_started_at ON agent_runs(org_id, started_at DESC);
CREATE INDEX idx_agent_runs_user_id_started_at ON agent_runs(user_id, started_at DESC);
CREATE INDEX idx_agent_runs_team_id_started_at ON agent_runs(team_id, started_at DESC);
CREATE INDEX idx_agent_runs_status ON agent_runs(status) WHERE status = 'RUNNING';
```

### 2.7 `budgets`

```sql
CREATE TABLE budgets (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                  UUID NOT NULL REFERENCES organizations(id),
    scope                   VARCHAR(20) NOT NULL,
                            -- CHECK (scope IN ('ORGANIZATION', 'TEAM'))
    scope_id                UUID NOT NULL,           -- org_id or team_id
    monthly_limit           NUMERIC(18,6) NOT NULL,
    thresholds              JSONB NOT NULL DEFAULT '[0.5, 0.8, 1.0]',
    notification_channels   JSONB NOT NULL DEFAULT '["IN_APP", "EMAIL"]',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(org_id, scope, scope_id)
);

CREATE INDEX idx_budgets_org_id ON budgets(org_id);
```

### 2.8 `budget_notifications`

```sql
CREATE TABLE budget_notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    budget_id       UUID NOT NULL REFERENCES budgets(id),
    threshold       NUMERIC(5,2) NOT NULL,           -- e.g. 0.50, 0.80, 1.00
    month           DATE NOT NULL,                   -- first day of the month
    notified_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(budget_id, threshold, month)
);
```

### 2.9 `exports`

```sql
CREATE TABLE exports (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          UUID NOT NULL REFERENCES organizations(id),
    requested_by    UUID NOT NULL REFERENCES users(id),
    report_type     VARCHAR(50) NOT NULL,
    filters         JSONB NOT NULL DEFAULT '{}',
    status          VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
                    -- CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED'))
    file_path       VARCHAR(500),
    row_count       INTEGER,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    expires_at      TIMESTAMPTZ
);

CREATE INDEX idx_exports_org_id ON exports(org_id);
```

## 3. ClickHouse Schema

### 3.1 `agent_runs` (analytics table)

```sql
CREATE TABLE agent_runs (
    run_id          UUID,
    org_id          UUID,
    team_id         UUID,
    user_id         UUID,
    agent_type_slug LowCardinality(String),
    model_name      LowCardinality(String),
    status          Enum8('RUNNING' = 1, 'SUCCEEDED' = 2, 'FAILED' = 3, 'CANCELLED' = 4),
    started_at      DateTime64(3, 'UTC'),
    finished_at     Nullable(DateTime64(3, 'UTC')),
    duration_ms     UInt64,
    input_tokens    UInt64,
    output_tokens   UInt64,
    total_tokens    UInt64,
    input_cost      Decimal(18, 6),
    output_cost     Decimal(18, 6),
    total_cost      Decimal(18, 6),
    error_category  LowCardinality(Nullable(String)),

    -- Denormalized for query performance
    team_name       String,
    user_display_name String,
    agent_type_display_name String
)
ENGINE = MergeTree()
PARTITION BY (org_id, toYYYYMM(started_at))
ORDER BY (org_id, started_at, team_id, user_id)
TTL started_at + INTERVAL 2 YEAR;
```

Partitioning by `(org_id, month)` ensures org isolation at the storage level and efficient pruning.

### 3.2 Materialized Views for Common Aggregations

#### Daily summary per org

```sql
CREATE MATERIALIZED VIEW agent_runs_daily_org_mv
ENGINE = SummingMergeTree()
PARTITION BY (org_id, toYYYYMM(day))
ORDER BY (org_id, day, agent_type_slug, status)
AS SELECT
    org_id,
    toDate(started_at) AS day,
    agent_type_slug,
    status,
    count()                     AS run_count,
    sum(total_tokens)           AS total_tokens,
    sum(total_cost)             AS total_cost,
    sum(duration_ms)            AS total_duration_ms
FROM agent_runs
GROUP BY org_id, day, agent_type_slug, status;
```

#### Daily summary per team

```sql
CREATE MATERIALIZED VIEW agent_runs_daily_team_mv
ENGINE = SummingMergeTree()
PARTITION BY (org_id, toYYYYMM(day))
ORDER BY (org_id, team_id, day, agent_type_slug, status)
AS SELECT
    org_id,
    team_id,
    toDate(started_at) AS day,
    agent_type_slug,
    status,
    count()                     AS run_count,
    sum(total_tokens)           AS total_tokens,
    sum(total_cost)             AS total_cost,
    sum(duration_ms)            AS total_duration_ms
FROM agent_runs
GROUP BY org_id, team_id, day, agent_type_slug, status;
```

#### Daily summary per user

```sql
CREATE MATERIALIZED VIEW agent_runs_daily_user_mv
ENGINE = SummingMergeTree()
PARTITION BY (org_id, toYYYYMM(day))
ORDER BY (org_id, user_id, day, agent_type_slug, status)
AS SELECT
    org_id,
    user_id,
    team_id,
    toDate(started_at) AS day,
    agent_type_slug,
    status,
    count()                     AS run_count,
    sum(total_tokens)           AS total_tokens,
    sum(total_cost)             AS total_cost,
    sum(duration_ms)            AS total_duration_ms
FROM agent_runs
GROUP BY org_id, user_id, team_id, day, agent_type_slug, status;
```

## 4. Data Ingestion

```
Agent Platform ──► Kafka Topic (agent-run-events) ──► Consumer Service ──┬──► PostgreSQL (agent_runs)
                                                                         └──► ClickHouse (agent_runs)
```

1. The agent platform publishes an event to a Kafka topic whenever a run starts, completes, or fails.
2. A Java consumer service (Spring Kafka) reads from the topic and writes to both PostgreSQL and ClickHouse.
3. Events are idempotent — duplicate events for the same `run_id` upsert rather than duplicate.
4. For ClickHouse, the consumer batches inserts (every 1 second or 1000 events, whichever comes first) using the ClickHouse JDBC driver's batch API.

### Event Schema (Kafka)

```json
{
  "event_type": "RUN_COMPLETED",
  "run_id": "run-uuid",
  "org_id": "org-uuid",
  "team_id": "team-uuid",
  "user_id": "user-uuid",
  "agent_type": "code_review",
  "model_name": "claude-sonnet-4",
  "model_version": "20250514",
  "status": "SUCCEEDED",
  "started_at": "2026-01-15T08:30:00Z",
  "finished_at": "2026-01-15T08:30:34Z",
  "input_tokens": 350000,
  "output_tokens": 130000,
  "input_cost": "0.105000",
  "output_cost": "0.093000",
  "error_category": null,
  "error_message": null,
  "timestamp": "2026-01-15T08:30:35Z"
}
```

## 5. Data Retention

| Store | Retention |
|---|---|
| ClickHouse raw events | 2 years (TTL on table) |
| ClickHouse materialized views | 3 years |
| PostgreSQL `agent_runs` | 1 year hot, then archived to cold storage (S3) |
| PostgreSQL reference tables | Indefinite |
| Exports (S3) | 1 hour after generation (signed URL expiry) |

## 6. Entity Relationship Diagram

```
organizations ─┬─── teams ─────── user_teams ───── users
               │                                    │
               ├─── agent_types                     │
               │                                    │
               ├─── budgets                         │
               │      └── budget_notifications      │
               │                                    │
               ├─── exports ────────────────────────┘
               │
               └─── agent_runs (PG) ────────────────┘
                    agent_runs (ClickHouse) ─── materialized views
```
