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
- JSON field names use **camelCase** (e.g., `totalRuns`, `orgId`, `successRate`), matching Java record/bean property naming.
- Null fields are omitted from responses (`default-property-inclusion: non_null`).
- Pagination:
  - **Limit-based:** `?limit=<int>` (default 50, max 200) with `hasMore` flag in response. Used for user run lists.
  - **Page-based:** `?page=<int>&size=<int>` (default page 0, default size 25, max 100) with `totalPages` and `totalElements` in response. Used for org-wide runs list.
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

Every request (except login) must include an `Authorization: Bearer <JWT>` header. The JWT is issued by the login endpoint and contains:

```json
{
  "sub": "user-uuid",
  "org_id": "org-uuid",
  "roles": ["ORG_ADMIN"],
  "teams": ["team-uuid-1", "team-uuid-2"]
}
```

The backend validates the JWT signature using a configured secret key. Org-scoping is enforced at the service layer — every query is filtered by `org_id` extracted from the token.

## 4. API Endpoints

### 4.1 Authentication

#### `POST /api/v1/auth/login`

Authenticates a user with email and password, returning a JWT token and user context.

**Request Body:**

```json
{
  "email": "alice@example.com",
  "password": "secret"
}
```

| Field | Type | Required | Description |
|---|---|---|---|
| `email` | string | yes | User's email address |
| `password` | string | yes | User's password |

**Response `200 OK`:**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "userId": "user-uuid",
  "orgId": "org-uuid",
  "orgName": "Acme Corp",
  "email": "alice@example.com",
  "displayName": "Alice Chen",
  "role": "ORG_ADMIN",
  "teams": [
    {
      "teamId": "team-uuid-1",
      "teamName": "Platform"
    }
  ]
}
```

**Response `401 Unauthorized`:**

```json
{
  "type": "https://analytics.example.com/errors/unauthorized",
  "title": "Unauthorized",
  "status": 401,
  "detail": "Invalid email or password"
}
```

**Authorization:** Public (no JWT required).

---

### 4.2 Organization Analytics

#### `GET /api/v1/orgs/{orgId}/analytics/summary`

Returns aggregate metrics for the organization over a date range.

**Query Parameters:**

| Param | Type | Required | Description |
|---|---|---|---|
| `from` | ISO date | yes | Start date (inclusive) |
| `to` | ISO date | yes | End date (inclusive) |
| `team_id` | UUID | no | Filter to a specific team |
| `agent_type` | string | no | Filter to a specific agent type slug |
| `status` | enum | no | Filter by run status: `SUCCEEDED`, `FAILED`, `RUNNING`, `CANCELLED` |

**Response `200 OK`:**

```json
{
  "orgId": "org-uuid",
  "period": { "from": "2026-01-01", "to": "2026-01-31" },
  "totalRuns": 142857,
  "succeededRuns": 135000,
  "failedRuns": 7500,
  "cancelledRuns": 357,
  "runningRuns": 0,
  "successRate": 0.9451,
  "totalTokens": 58000000000,
  "totalInputTokens": 42000000000,
  "totalOutputTokens": 16000000000,
  "totalCost": "24350.120000",
  "avgDurationMs": 34500,
  "p50DurationMs": 28000,
  "p95DurationMs": 95000,
  "p99DurationMs": 180000
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
  "orgId": "org-uuid",
  "granularity": "DAILY",
  "dataPoints": [
    {
      "timestamp": "2026-01-01T00:00:00Z",
      "totalRuns": 4500,
      "succeededRuns": 4300,
      "failedRuns": 180,
      "totalTokens": 1800000000,
      "totalCost": "780.500000",
      "avgDurationMs": 33000
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
  "orgId": "org-uuid",
  "period": { "from": "2026-01-01", "to": "2026-01-31" },
  "teams": [
    {
      "teamId": "team-uuid-1",
      "teamName": "Platform",
      "totalRuns": 45000,
      "totalTokens": 18000000000,
      "totalCost": "7800.000000",
      "successRate": 0.96,
      "avgDurationMs": 31000
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
  "orgId": "org-uuid",
  "period": { "from": "2026-01-01", "to": "2026-01-31" },
  "agentTypes": [
    {
      "agentType": "code_review",
      "displayName": "Code Review Agent",
      "totalRuns": 60000,
      "totalTokens": 25000000000,
      "totalCost": "10500.000000",
      "successRate": 0.97,
      "avgDurationMs": 28000
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
  "orgId": "org-uuid",
  "sortBy": "cost",
  "users": [
    {
      "userId": "user-uuid-1",
      "displayName": "Alice Chen",
      "email": "alice@example.com",
      "teamName": "Platform",
      "totalRuns": 3200,
      "totalTokens": 1500000000,
      "totalCost": "620.000000"
    }
  ]
}
```

**Authorization:** `ORG_ADMIN` only.

---

### 4.3 Organization Runs

#### `GET /api/v1/orgs/{orgId}/runs`

Returns a page-based paginated list of all agent runs across the organization with advanced filtering. Supports multi-value status filtering.

**Query Parameters:**

| Param | Type | Required | Description |
|---|---|---|---|
| `from` | ISO date | yes | Start date (inclusive) |
| `to` | ISO date | yes | End date (inclusive) |
| `team_id` | UUID | no | Filter to a specific team |
| `user_id` | UUID | no | Filter to a specific user |
| `status` | string[] | no | Filter by run status (repeatable): `SUCCEEDED`, `FAILED`, `RUNNING`, `CANCELLED` |
| `agent_type` | string | no | Filter to a specific agent type slug |
| `page` | int | no | Page number, zero-indexed (default `0`) |
| `size` | int | no | Page size (default `25`, max `100`) |

**Response `200 OK`:**

```json
{
  "runs": [
    {
      "runId": "run-uuid-1",
      "userId": "user-uuid",
      "userName": "Alice Chen",
      "teamId": "team-uuid-1",
      "teamName": "Platform",
      "agentType": "code_review",
      "agentTypeDisplayName": "Code Review Agent",
      "status": "SUCCEEDED",
      "startedAt": "2026-01-15T08:30:00Z",
      "finishedAt": "2026-01-15T08:30:34Z",
      "durationMs": 34000,
      "totalTokens": 480000,
      "totalCost": "0.198000"
    }
  ],
  "page": 0,
  "totalPages": 12,
  "totalElements": 285
}
```

**Authorization:** `ORG_ADMIN` only.

---

### 4.4 Team Analytics

#### `GET /api/v1/teams/{teamId}/analytics/summary`

Same structure as org summary, scoped to a team.

**Query Parameters:** `from`, `to`, `agent_type`, `status`.

**Authorization:** `ORG_ADMIN`, `TEAM_LEAD` (own teams only).

---

#### `GET /api/v1/teams/{teamId}/analytics/timeseries`

Same structure as org timeseries, scoped to a team.

**Query Parameters:** `from`, `to`, `agent_type`, `status`, `granularity`.

**Authorization:** `ORG_ADMIN`, `TEAM_LEAD` (own teams only).

---

#### `GET /api/v1/teams/{teamId}/analytics/by-user`

Returns per-user breakdown within the team. Uses the same `ByTeamResponse` structure, where each entry represents a user instead of a team.

**Query Parameters:** `from`, `to`, `agent_type`, `status`.

**Response `200 OK`:**

```json
{
  "orgId": "org-uuid",
  "period": { "from": "2026-01-01", "to": "2026-01-31" },
  "teams": [
    {
      "teamId": "user-uuid-1",
      "teamName": "Alice Chen",
      "totalRuns": 3200,
      "totalTokens": 1500000000,
      "totalCost": "620.000000",
      "successRate": 0.98,
      "avgDurationMs": 27000
    }
  ]
}
```

> **Note:** This endpoint reuses the `ByTeamResponse` structure. In this context, `teamId` contains the user ID and `teamName` contains the user's display name.

**Authorization:** `ORG_ADMIN`, `TEAM_LEAD` (own teams only).

---

### 4.5 User / Personal Analytics

#### `GET /api/v1/users/me/analytics/summary`

Returns aggregate metrics for the authenticated user.

**Query Parameters:** `from`, `to`, `agent_type`, `status`.

**Response `200 OK`:**

```json
{
  "userId": "user-uuid",
  "displayName": "Alice Chen",
  "period": { "from": "2026-01-01", "to": "2026-01-31" },
  "totalRuns": 320,
  "succeededRuns": 310,
  "failedRuns": 10,
  "totalTokens": 150000000,
  "totalCost": "62.400000",
  "avgDurationMs": 29000,
  "teamRank": 3,
  "teamSize": 12
}
```

**Authorization:** Any authenticated user (returns own data only).

---

#### `GET /api/v1/users/me/analytics/timeseries`

Returns daily personal usage for charting. Same response structure as org timeseries (with `orgId` set to `null`).

**Query Parameters:** `from`, `to`, `agent_type`, `status`.

**Authorization:** Any authenticated user.

---

#### `GET /api/v1/users/me/runs`

Returns the user's agent runs, limited to the most recent N results.

**Query Parameters:**

| Param | Type | Required | Description |
|---|---|---|---|
| `from` | ISO date | yes | Start date (inclusive) |
| `to` | ISO date | yes | End date (inclusive) |
| `agent_type` | string | no | Filter to a specific agent type slug |
| `status` | enum | no | Filter by run status |
| `limit` | int | no | Max results to return (default `50`, max `200`) |

**Response `200 OK`:**

```json
{
  "runs": [
    {
      "runId": "run-uuid-1",
      "agentType": "code_review",
      "agentTypeDisplayName": "Code Review Agent",
      "status": "SUCCEEDED",
      "startedAt": "2026-01-15T08:30:00Z",
      "finishedAt": "2026-01-15T08:30:34Z",
      "durationMs": 34000,
      "totalTokens": 480000,
      "totalCost": "0.198000"
    }
  ],
  "nextCursor": null,
  "hasMore": true
}
```

> **Note:** The `nextCursor` field is reserved for future cursor-based pagination support and is currently always `null`. The `hasMore` flag indicates whether additional runs exist beyond the returned `limit`.

**Authorization:** Any authenticated user (returns own runs only).

---

#### `GET /api/v1/users/{userId}/analytics/summary`

Returns aggregate metrics for a specific user. Same response structure as `GET /api/v1/users/me/analytics/summary`.

**Query Parameters:** `from`, `to`, `agent_type`, `status`.

**Authorization:** `ORG_ADMIN`, `TEAM_LEAD` (must share at least one team with the target user).

---

#### `GET /api/v1/users/{userId}/analytics/timeseries`

Returns daily usage for a specific user. Same response structure as `GET /api/v1/users/me/analytics/timeseries`.

**Query Parameters:** `from`, `to`, `agent_type`, `status`.

**Authorization:** `ORG_ADMIN`, `TEAM_LEAD` (must share at least one team with the target user).

---

#### `GET /api/v1/users/{userId}/runs`

Returns agent runs for a specific user. Same response structure and query parameters as `GET /api/v1/users/me/runs`.

**Query Parameters:** `from`, `to`, `agent_type`, `status`, `limit`.

**Authorization:** `ORG_ADMIN`, `TEAM_LEAD` (must share at least one team with the target user).

---

#### `GET /api/v1/runs/{runId}`

Returns full details for a single agent run.

**Response `200 OK`:**

```json
{
  "runId": "run-uuid-1",
  "orgId": "org-uuid",
  "teamId": "team-uuid-1",
  "userId": "user-uuid",
  "agentType": "code_review",
  "agentTypeDisplayName": "Code Review Agent",
  "modelName": "claude-sonnet-4",
  "modelVersion": "20250514",
  "status": "SUCCEEDED",
  "startedAt": "2026-01-15T08:30:00Z",
  "finishedAt": "2026-01-15T08:30:34Z",
  "durationMs": 34000,
  "inputTokens": 350000,
  "outputTokens": 130000,
  "totalTokens": 480000,
  "inputCost": "0.105000",
  "outputCost": "0.093000",
  "totalCost": "0.198000",
  "errorCategory": null,
  "errorMessage": null
}
```

**Authorization:** Owner of the run, their team lead (if the run belongs to one of the lead's teams), or org admin.

---

### 4.6 Budget & Alerts

#### `GET /api/v1/orgs/{orgId}/budgets`

Returns all configured budgets. The response serializes the `Budget` entity directly.

**Response `200 OK`:**

```json
{
  "budgets": [
    {
      "id": "budget-uuid-1",
      "orgId": "org-uuid",
      "scope": "ORGANIZATION",
      "scopeId": "org-uuid",
      "monthlyLimit": 50000.000000,
      "thresholds": "0.50,0.80,1.00",
      "notificationChannels": "IN_APP,EMAIL",
      "createdAt": "2026-01-01T00:00:00Z",
      "updatedAt": "2026-01-15T12:00:00Z"
    }
  ]
}
```

> **Note:** `thresholds` and `notificationChannels` are stored and returned as comma-separated strings. `monthlyLimit` is serialized as a JSON number (BigDecimal).

**Authorization:** `ORG_ADMIN` only.

---

#### `PUT /api/v1/orgs/{orgId}/budgets/{budgetId}` *(planned — not yet implemented)*

Create or update a budget.

**Request Body:**

```json
{
  "scope": "TEAM",
  "scopeId": "team-uuid-1",
  "monthlyLimit": "15000.000000",
  "thresholds": [0.50, 0.80, 1.00],
  "notificationChannels": ["IN_APP", "EMAIL"]
}
```

**Authorization:** `ORG_ADMIN` only.

---

#### `DELETE /api/v1/orgs/{orgId}/budgets/{budgetId}` *(planned — not yet implemented)*

Delete a budget.

**Authorization:** `ORG_ADMIN` only.

---

### 4.7 Export *(planned — not yet implemented)*

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

### 4.8 Reference Data

#### `GET /api/v1/orgs/{orgId}/teams`

Returns list of teams in the organization (for filter dropdowns).

**Response `200 OK`:**

```json
{
  "teams": [
    {
      "team_id": "team-uuid-1",
      "name": "Platform"
    },
    {
      "team_id": "team-uuid-2",
      "name": "Data Science"
    }
  ]
}
```

**Authorization:** Any authenticated user in the organization.

---

#### `GET /api/v1/orgs/{orgId}/agent-types`

Returns list of agent types used in the organization (for filter dropdowns).

**Response `200 OK`:**

```json
{
  "agent_types": [
    {
      "slug": "code_review",
      "display_name": "Code Review Agent"
    },
    {
      "slug": "test_generation",
      "display_name": "Test Generation Agent"
    }
  ]
}
```

**Authorization:** Any authenticated user in the organization.

---

#### `GET /api/v1/orgs/{orgId}/users`

Returns list of users in the organization (for filter dropdowns).

**Response `200 OK`:**

```json
{
  "users": [
    {
      "user_id": "user-uuid-1",
      "display_name": "Alice Chen",
      "email": "alice@example.com"
    },
    {
      "user_id": "user-uuid-2",
      "display_name": "Bob Smith",
      "email": "bob@example.com"
    }
  ]
}
```

**Authorization:** `ORG_ADMIN` only.

> **Note:** The reference data endpoints (`/teams`, `/agent-types`, `/users`) use explicit JSON key names (snake_case) via `Map` responses, unlike the DTO-based analytics endpoints which use camelCase.

---

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
