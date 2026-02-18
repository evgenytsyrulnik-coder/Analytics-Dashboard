# Backend API Specification

## 1. Technology Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.x |
| API Style | REST (JSON) |
| Auth | Spring Security with OAuth 2.0 Resource Server (JWT validation) |
| Database | PostgreSQL 16 (transactional), Apache Druid or ClickHouse (analytics OLAP) |
| Cache | Redis |
| Build | Gradle (Kotlin DSL) |
| Containerization | Docker, Helm for Kubernetes |

## 2. General Conventions

- Base path: `/api/v1`
- All timestamps in ISO 8601 UTC (`2026-01-15T08:30:00Z`).
- All monetary amounts in USD as `BigDecimal` strings with 6 decimal places (e.g., `"0.004200"`).
- Pagination: cursor-based using `?cursor=<opaque>&limit=<int>` (default limit 50, max 200).
- Errors follow RFC 7807 Problem Details:
  ```json
  {
    "type": "https://analytics.example.com/errors/not-found",
    "title": "Not Found",
    "status": 404,
    "detail": "Agent run abc-123 does not exist.",
    "instance": "/api/v1/runs/abc-123"
  }
  ```
- Rate limiting: 100 requests/minute per user, 1000 requests/minute per organization. HTTP 429 returned when exceeded with `Retry-After` header.

## 3. Authentication

Every request must include an `Authorization: Bearer <JWT>` header. The JWT is issued by the platform's identity provider and contains:

```json
{
  "sub": "user-uuid",
  "org_id": "org-uuid",
  "roles": ["ORG_ADMIN"],
  "teams": ["team-uuid-1", "team-uuid-2"]
}
```

The backend validates the JWT signature against the IdP's JWKS endpoint. Org-scoping is enforced at the service layer — every query is filtered by `org_id` extracted from the token.

## 4. API Endpoints

### 4.1 Organization Analytics

#### `GET /api/v1/orgs/{orgId}/analytics/summary`

Returns aggregate metrics for the organization over a date range.

**Query Parameters:**

| Param | Type | Required | Description |
|---|---|---|---|
| `from` | ISO date | yes | Start date (inclusive) |
| `to` | ISO date | yes | End date (inclusive) |
| `team_id` | UUID | no | Filter to a specific team |
| `agent_type` | string | no | Filter to a specific agent type |
| `status` | enum | no | Filter by run status: `SUCCEEDED`, `FAILED`, `RUNNING`, `CANCELLED` |

**Response `200 OK`:**

```json
{
  "org_id": "org-uuid",
  "period": { "from": "2026-01-01", "to": "2026-01-31" },
  "total_runs": 142857,
  "succeeded_runs": 135000,
  "failed_runs": 7500,
  "cancelled_runs": 357,
  "running_runs": 0,
  "success_rate": 0.9451,
  "total_tokens": 58000000000,
  "total_input_tokens": 42000000000,
  "total_output_tokens": 16000000000,
  "total_cost": "24350.120000",
  "avg_duration_ms": 34500,
  "p50_duration_ms": 28000,
  "p95_duration_ms": 95000,
  "p99_duration_ms": 180000
}
```

**Authorization:** `ORG_ADMIN` only.

---

#### `GET /api/v1/orgs/{orgId}/analytics/timeseries`

Returns daily aggregated metrics for charting.

**Query Parameters:** Same as summary plus:

| Param | Type | Required | Description |
|---|---|---|---|
| `granularity` | enum | no | `DAILY` (default), `HOURLY`, `WEEKLY` |

**Response `200 OK`:**

```json
{
  "org_id": "org-uuid",
  "granularity": "DAILY",
  "data_points": [
    {
      "timestamp": "2026-01-01T00:00:00Z",
      "total_runs": 4500,
      "succeeded_runs": 4300,
      "failed_runs": 180,
      "total_tokens": 1800000000,
      "total_cost": "780.500000",
      "avg_duration_ms": 33000
    }
  ]
}
```

**Authorization:** `ORG_ADMIN` only.

---

#### `GET /api/v1/orgs/{orgId}/analytics/by-team`

Returns usage breakdown per team.

**Query Parameters:** `from`, `to`, `agent_type`, `status`.

**Response `200 OK`:**

```json
{
  "org_id": "org-uuid",
  "period": { "from": "2026-01-01", "to": "2026-01-31" },
  "teams": [
    {
      "team_id": "team-uuid-1",
      "team_name": "Platform",
      "total_runs": 45000,
      "total_tokens": 18000000000,
      "total_cost": "7800.000000",
      "success_rate": 0.96,
      "avg_duration_ms": 31000
    }
  ]
}
```

**Authorization:** `ORG_ADMIN` only.

---

#### `GET /api/v1/orgs/{orgId}/analytics/by-agent-type`

Returns usage breakdown per agent type.

**Query Parameters:** `from`, `to`, `team_id`, `status`.

**Response `200 OK`:**

```json
{
  "org_id": "org-uuid",
  "period": { "from": "2026-01-01", "to": "2026-01-31" },
  "agent_types": [
    {
      "agent_type": "code_review",
      "display_name": "Code Review Agent",
      "total_runs": 60000,
      "total_tokens": 25000000000,
      "total_cost": "10500.000000",
      "success_rate": 0.97,
      "avg_duration_ms": 28000
    }
  ]
}
```

**Authorization:** `ORG_ADMIN`, `TEAM_LEAD` (filtered to their teams).

---

#### `GET /api/v1/orgs/{orgId}/analytics/top-users`

Returns top N users by a given metric.

**Query Parameters:** `from`, `to`, `team_id`, `sort_by` (enum: `runs`, `tokens`, `cost`; default `runs`), `limit` (default 10, max 50).

**Response `200 OK`:**

```json
{
  "org_id": "org-uuid",
  "sort_by": "cost",
  "users": [
    {
      "user_id": "user-uuid-1",
      "display_name": "Alice Chen",
      "email": "alice@example.com",
      "team_name": "Platform",
      "total_runs": 3200,
      "total_tokens": 1500000000,
      "total_cost": "620.000000"
    }
  ]
}
```

**Authorization:** `ORG_ADMIN` only.

---

### 4.2 Team Analytics

#### `GET /api/v1/teams/{teamId}/analytics/summary`

Same structure as org summary, scoped to a team.

**Authorization:** `ORG_ADMIN`, `TEAM_LEAD` (own teams only).

---

#### `GET /api/v1/teams/{teamId}/analytics/timeseries`

Same structure as org timeseries, scoped to a team.

**Authorization:** `ORG_ADMIN`, `TEAM_LEAD` (own teams only).

---

#### `GET /api/v1/teams/{teamId}/analytics/by-user`

Returns per-user breakdown within the team.

**Query Parameters:** `from`, `to`, `agent_type`, `status`.

**Response `200 OK`:**

```json
{
  "team_id": "team-uuid-1",
  "period": { "from": "2026-01-01", "to": "2026-01-31" },
  "users": [
    {
      "user_id": "user-uuid-1",
      "display_name": "Alice Chen",
      "total_runs": 3200,
      "total_tokens": 1500000000,
      "total_cost": "620.000000",
      "success_rate": 0.98,
      "avg_duration_ms": 27000
    }
  ]
}
```

**Authorization:** `ORG_ADMIN`, `TEAM_LEAD` (own teams only).

---

### 4.3 User / Personal Analytics

#### `GET /api/v1/users/me/analytics/summary`

Returns aggregate metrics for the authenticated user.

**Query Parameters:** `from`, `to`, `agent_type`, `status`.

**Response `200 OK`:**

```json
{
  "user_id": "user-uuid",
  "period": { "from": "2026-01-01", "to": "2026-01-31" },
  "total_runs": 320,
  "succeeded_runs": 310,
  "failed_runs": 10,
  "total_tokens": 150000000,
  "total_cost": "62.400000",
  "avg_duration_ms": 29000,
  "team_rank": 3,
  "team_size": 12
}
```

**Authorization:** Any authenticated user (returns own data only).

---

#### `GET /api/v1/users/me/analytics/timeseries`

Returns daily personal usage for charting.

**Authorization:** Any authenticated user.

---

#### `GET /api/v1/users/me/runs`

Returns paginated list of the user's agent runs.

**Query Parameters:** `from`, `to`, `agent_type`, `status`, `cursor`, `limit`.

**Response `200 OK`:**

```json
{
  "runs": [
    {
      "run_id": "run-uuid-1",
      "agent_type": "code_review",
      "agent_type_display_name": "Code Review Agent",
      "status": "SUCCEEDED",
      "started_at": "2026-01-15T08:30:00Z",
      "finished_at": "2026-01-15T08:30:34Z",
      "duration_ms": 34000,
      "total_tokens": 480000,
      "total_cost": "0.198000"
    }
  ],
  "next_cursor": "eyJsYXN0X2lkIjoicnVuLXV1aWQtNTAifQ==",
  "has_more": true
}
```

**Authorization:** Any authenticated user (returns own runs only).

---

#### `GET /api/v1/runs/{runId}`

Returns full details for a single agent run.

**Response `200 OK`:**

```json
{
  "run_id": "run-uuid-1",
  "org_id": "org-uuid",
  "team_id": "team-uuid-1",
  "user_id": "user-uuid",
  "agent_type": "code_review",
  "agent_type_display_name": "Code Review Agent",
  "model_name": "claude-sonnet-4",
  "model_version": "20250514",
  "status": "SUCCEEDED",
  "started_at": "2026-01-15T08:30:00Z",
  "finished_at": "2026-01-15T08:30:34Z",
  "duration_ms": 34000,
  "input_tokens": 350000,
  "output_tokens": 130000,
  "total_tokens": 480000,
  "input_cost": "0.105000",
  "output_cost": "0.093000",
  "total_cost": "0.198000",
  "error_category": null,
  "error_message": null
}
```

**Authorization:** Owner of the run, their team lead, or org admin.

---

### 4.4 Budget & Alerts

#### `GET /api/v1/orgs/{orgId}/budgets`

Returns all configured budgets.

**Response `200 OK`:**

```json
{
  "budgets": [
    {
      "budget_id": "budget-uuid-1",
      "scope": "ORGANIZATION",
      "scope_id": "org-uuid",
      "monthly_limit": "50000.000000",
      "current_spend": "24350.120000",
      "utilization": 0.487,
      "thresholds": [0.50, 0.80, 1.00],
      "notification_channels": ["IN_APP", "EMAIL"]
    },
    {
      "budget_id": "budget-uuid-2",
      "scope": "TEAM",
      "scope_id": "team-uuid-1",
      "monthly_limit": "15000.000000",
      "current_spend": "7800.000000",
      "utilization": 0.52,
      "thresholds": [0.50, 0.80, 1.00],
      "notification_channels": ["IN_APP", "EMAIL"]
    }
  ]
}
```

**Authorization:** `ORG_ADMIN` only.

---

#### `PUT /api/v1/orgs/{orgId}/budgets/{budgetId}`

Create or update a budget.

**Request Body:**

```json
{
  "scope": "TEAM",
  "scope_id": "team-uuid-1",
  "monthly_limit": "15000.000000",
  "thresholds": [0.50, 0.80, 1.00],
  "notification_channels": ["IN_APP", "EMAIL"]
}
```

**Authorization:** `ORG_ADMIN` only.

---

#### `DELETE /api/v1/orgs/{orgId}/budgets/{budgetId}`

Delete a budget.

**Authorization:** `ORG_ADMIN` only.

---

### 4.5 Export

#### `POST /api/v1/orgs/{orgId}/exports`

Triggers an async CSV export of the current analytics view.

**Request Body:**

```json
{
  "report_type": "ORG_SUMMARY",
  "from": "2026-01-01",
  "to": "2026-01-31",
  "filters": {
    "team_id": null,
    "agent_type": null,
    "status": null
  }
}
```

`report_type` enum: `ORG_SUMMARY`, `TEAM_SUMMARY`, `USER_RUNS`, `BY_TEAM`, `BY_AGENT_TYPE`, `BY_USER`.

**Response `202 Accepted`:**

```json
{
  "export_id": "export-uuid-1",
  "status": "PROCESSING",
  "created_at": "2026-01-31T12:00:00Z"
}
```

---

#### `GET /api/v1/orgs/{orgId}/exports/{exportId}`

Check export status and retrieve download URL.

**Response `200 OK`:**

```json
{
  "export_id": "export-uuid-1",
  "status": "COMPLETED",
  "download_url": "https://storage.example.com/exports/export-uuid-1.csv?token=signed",
  "expires_at": "2026-01-31T13:00:00Z",
  "row_count": 4500
}
```

**Authorization:** `ORG_ADMIN`, `TEAM_LEAD` (for team-scoped reports).

---

### 4.6 Reference Data

#### `GET /api/v1/orgs/{orgId}/teams`

Returns list of teams in the organization (for filter dropdowns).

#### `GET /api/v1/orgs/{orgId}/agent-types`

Returns list of agent types used in the organization (for filter dropdowns).

## 5. Error Codes

| HTTP Status | `type` suffix | When |
|---|---|---|
| 400 | `/errors/bad-request` | Malformed query params, invalid date range |
| 401 | `/errors/unauthorized` | Missing or invalid JWT |
| 403 | `/errors/forbidden` | User lacks required role for this endpoint |
| 404 | `/errors/not-found` | Resource does not exist or is not in user's org |
| 429 | `/errors/rate-limited` | Rate limit exceeded |
| 500 | `/errors/internal` | Unexpected server error |

## 6. Versioning

The API is versioned via URL path (`/api/v1/`). Breaking changes result in a new version (`/api/v2/`). Non-breaking additions (new optional fields, new endpoints) are added to the current version.
