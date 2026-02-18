# Testing Specification — TDD & Acceptance Testing

## 1. Overview

This document defines the testing strategy, TDD workflow, and acceptance test criteria for the Agent Analytics Dashboard. All features are developed test-first: tests are written before production code, and acceptance criteria drive end-to-end validation of each user-facing requirement.

### 1.1 Testing Pyramid

```
                    ┌───────────────┐
                    │   E2E /       │   Playwright
                    │  Acceptance   │   (critical journeys)
                  ┌─┴───────────────┴─┐
                  │   Contract Tests   │   Spring Cloud Contract / Pact
                ┌─┴───────────────────┴─┐
                │   Integration Tests    │   Testcontainers (PG, CH, Redis, Kafka)
              ┌─┴───────────────────────┴─┐
              │      Unit Tests            │   JUnit 5 + Mockito (backend)
            ┌─┴───────────────────────────┴─┐   Vitest + RTL (frontend)
            │      Static Analysis           │   SpotBugs, Checkstyle, ESLint, TypeScript
            └─────────────────────────────────┘
```

### 1.2 Coverage Targets

| Layer | Framework | Target |
|---|---|---|
| Backend unit tests | JUnit 5 + Mockito | >= 80% line coverage on service layer |
| Backend integration tests | Spring Boot Test + Testcontainers | 100% of repository classes, 100% of API endpoints |
| Contract tests | Spring Cloud Contract / Pact | All frontend-backend API contracts |
| Frontend unit tests | Vitest + React Testing Library | >= 70% line coverage |
| E2E acceptance tests | Playwright | All critical user journeys (see Section 7) |
| Load tests | Gatling (Java DSL) | Verify p95/p99 latency targets under expected concurrency |

---

## 2. TDD Workflow

### 2.1 Red-Green-Refactor Cycle

Every feature and bug fix follows the strict TDD cycle:

```
  ┌──────────────────────────────────────────────────────┐
  │                                                      │
  │   1. RED     Write a failing test that defines       │
  │              the desired behavior                    │
  │                        │                             │
  │                        ▼                             │
  │   2. GREEN   Write the minimum production code       │
  │              to make the test pass                   │
  │                        │                             │
  │                        ▼                             │
  │   3. REFACTOR  Improve the code while keeping        │
  │                all tests green                       │
  │                        │                             │
  │                        ▼                             │
  │              Commit. Repeat for next behavior.       │
  │                                                      │
  └──────────────────────────────────────────────────────┘
```

### 2.2 TDD Rules

1. **No production code without a failing test.** Every line of production code must be justified by a test that fails without it.
2. **Write only enough test to fail.** A compile error counts as a failure. Do not write more test code than is needed to demonstrate a single missing behavior.
3. **Write only enough production code to pass.** Do not add speculative functionality. The refactor step handles design improvement.
4. **Commit at each green state.** Each passing test suite state is a valid commit point. Commits should be small and frequent.
5. **Tests are first-class code.** Apply the same quality standards (naming, readability, no duplication) to test code as production code.

### 2.3 TDD Iteration by Layer

| Layer | TDD Entry Point | Cycle |
|---|---|---|
| Backend service | Write a unit test for a service method | Red → implement service → Green → Refactor |
| Backend repository | Write an integration test with Testcontainers | Red → implement repository → Green → Refactor |
| Backend controller | Write a MockMvc test for the endpoint | Red → implement controller → Green → Refactor |
| Frontend component | Write a RTL test for render + interaction | Red → implement component → Green → Refactor |
| Frontend hook/service | Write a Vitest test for the hook/API call | Red → implement hook → Green → Refactor |
| E2E acceptance | Write a Playwright test for the user journey | Red → implement feature end-to-end → Green → Refactor |

### 2.4 Commit Convention for TDD

Commits during TDD follow this naming pattern:

```
test(<scope>): <describe the failing test>           # RED phase
feat(<scope>): <describe the production code>         # GREEN phase
refactor(<scope>): <describe the improvement>         # REFACTOR phase
```

Examples:
```
test(analytics): add failing test for org summary endpoint with date range
feat(analytics): implement org summary query with date range filter
refactor(analytics): extract date range validation into shared utility
```

---

## 3. Backend Unit Tests

### 3.1 Scope

Unit tests cover all service-layer classes in isolation, mocking external dependencies (repositories, caches, external clients).

### 3.2 Framework & Configuration

```kotlin
// build.gradle.kts
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.10+")
    testImplementation("org.mockito:mockito-core:5.x")
    testImplementation("org.mockito:mockito-junit-jupiter:5.x")
    testImplementation("org.assertj:assertj-core:3.25+")
}
```

### 3.3 Naming Convention

Test classes: `{ClassName}Test.java`
Test methods: `should_{expected_behavior}_when_{condition}`

```java
class AnalyticsSummaryServiceTest {
    void should_returnAggregateMetrics_when_validDateRangeProvided() {}
    void should_throwBadRequest_when_dateRangeExceedsOneYear() {}
    void should_filterByTeam_when_teamIdProvided() {}
    void should_filterByStatus_when_statusProvided() {}
    void should_returnZeroCounts_when_noDataInRange() {}
}
```

### 3.4 Test Cases by Module

#### 3.4.1 Analytics Service

| Test ID | Method Under Test | Scenario | Expected Result |
|---|---|---|---|
| UT-AN-001 | `getOrgSummary` | Valid date range, no filters | Returns aggregate metrics for entire org |
| UT-AN-002 | `getOrgSummary` | Filter by team_id | Returns metrics scoped to that team |
| UT-AN-003 | `getOrgSummary` | Filter by agent_type | Returns metrics for that agent type only |
| UT-AN-004 | `getOrgSummary` | Filter by status=FAILED | Returns only failed run metrics |
| UT-AN-005 | `getOrgSummary` | Date range > 1 year | Throws `BadRequestException` |
| UT-AN-006 | `getOrgSummary` | `from` after `to` | Throws `BadRequestException` |
| UT-AN-007 | `getOrgSummary` | No data in range | Returns zero counts, null rates |
| UT-AN-008 | `getOrgTimeseries` | Daily granularity, 30-day range | Returns 30 data points |
| UT-AN-009 | `getOrgTimeseries` | Hourly granularity, 1-day range | Returns 24 data points |
| UT-AN-010 | `getOrgTimeseries` | Weekly granularity, 90-day range | Returns ~13 data points |
| UT-AN-011 | `getByTeamBreakdown` | Multiple teams with data | Returns sorted breakdown per team |
| UT-AN-012 | `getByAgentTypeBreakdown` | Multiple agent types | Returns breakdown per agent type |
| UT-AN-013 | `getTopUsers` | sort_by=cost, limit=10 | Returns top 10 users sorted by cost descending |
| UT-AN-014 | `getTopUsers` | limit exceeds max (50) | Clamps to max limit |
| UT-AN-015 | `getTeamSummary` | Valid team within user's org | Returns team-scoped metrics |
| UT-AN-016 | `getTeamSummary` | Team from different org | Throws `NotFoundException` |
| UT-AN-017 | `getUserSummary` | Authenticated user | Returns personal metrics with team rank |
| UT-AN-018 | `getUserSummary` | User with no runs | Returns zero counts, no rank |

#### 3.4.2 Budget Service

| Test ID | Method Under Test | Scenario | Expected Result |
|---|---|---|---|
| UT-BG-001 | `listBudgets` | Org with multiple budgets | Returns all budgets for the org |
| UT-BG-002 | `createBudget` | Valid org-scope budget | Persists budget, returns created entity |
| UT-BG-003 | `createBudget` | Valid team-scope budget | Persists budget scoped to team |
| UT-BG-004 | `createBudget` | Duplicate scope+scope_id | Throws `ConflictException` |
| UT-BG-005 | `createBudget` | Negative monthly_limit | Throws `BadRequestException` |
| UT-BG-006 | `updateBudget` | Change monthly_limit | Updates budget, returns updated entity |
| UT-BG-007 | `updateBudget` | Budget not found | Throws `NotFoundException` |
| UT-BG-008 | `deleteBudget` | Existing budget | Deletes budget and associated notifications |
| UT-BG-009 | `deleteBudget` | Budget not in user's org | Throws `NotFoundException` |
| UT-BG-010 | `checkThresholds` | Spend crosses 50% threshold | Creates notification, returns alert |
| UT-BG-011 | `checkThresholds` | Spend crosses 80% threshold | Creates notification at 80% level |
| UT-BG-012 | `checkThresholds` | Spend crosses 100% threshold | Creates notification at 100% level |
| UT-BG-013 | `checkThresholds` | Threshold already notified this month | Does not create duplicate notification |
| UT-BG-014 | `checkThresholds` | Custom thresholds [0.25, 0.75] | Checks custom thresholds only |

#### 3.4.3 Export Service

| Test ID | Method Under Test | Scenario | Expected Result |
|---|---|---|---|
| UT-EX-001 | `triggerExport` | Valid ORG_SUMMARY request | Creates export record with PROCESSING status |
| UT-EX-002 | `triggerExport` | Invalid report_type | Throws `BadRequestException` |
| UT-EX-003 | `getExportStatus` | Export COMPLETED | Returns status with download_url and row_count |
| UT-EX-004 | `getExportStatus` | Export PROCESSING | Returns PROCESSING status, no download_url |
| UT-EX-005 | `getExportStatus` | Export FAILED | Returns FAILED status with error info |
| UT-EX-006 | `getExportStatus` | Export from different org | Throws `NotFoundException` |
| UT-EX-007 | `processExport` | ORG_SUMMARY, 100k rows | Writes CSV to S3, updates record to COMPLETED |
| UT-EX-008 | `processExport` | ClickHouse query fails | Updates record to FAILED with error |

#### 3.4.4 Ingestion Service

| Test ID | Method Under Test | Scenario | Expected Result |
|---|---|---|---|
| UT-IG-001 | `processEvent` | RUN_COMPLETED event | Writes to both PG and ClickHouse |
| UT-IG-002 | `processEvent` | RUN_STARTED event | Creates RUNNING record in PG |
| UT-IG-003 | `processEvent` | Duplicate run_id | Upserts (idempotent), no duplicate |
| UT-IG-004 | `processEvent` | Missing required fields | Logs error, sends to DLQ |
| UT-IG-005 | `processEvent` | Unknown org_id | Logs warning, discards event |
| UT-IG-006 | `batchInsert` | 1000 events batch | Flushes batch to ClickHouse |
| UT-IG-007 | `batchInsert` | Timeout (1 second) with 500 events | Flushes partial batch |

#### 3.4.5 Authorization Service

| Test ID | Method Under Test | Scenario | Expected Result |
|---|---|---|---|
| UT-AU-001 | `authorize` | ORG_ADMIN accesses org endpoint | Grants access |
| UT-AU-002 | `authorize` | TEAM_LEAD accesses own team | Grants access |
| UT-AU-003 | `authorize` | TEAM_LEAD accesses other team | Throws `ForbiddenException` |
| UT-AU-004 | `authorize` | MEMBER accesses org-level data | Throws `ForbiddenException` |
| UT-AU-005 | `authorize` | MEMBER accesses own data | Grants access |
| UT-AU-006 | `authorize` | User accesses run they own | Grants access |
| UT-AU-007 | `authorize` | User accesses run from different org | Throws `NotFoundException` |
| UT-AU-008 | `authorize` | TEAM_LEAD accesses run from their team member | Grants access |
| UT-AU-009 | `parseJwt` | Valid JWT with all claims | Extracts sub, org_id, roles, teams |
| UT-AU-010 | `parseJwt` | Expired JWT | Throws `UnauthorizedException` |
| UT-AU-011 | `parseJwt` | Invalid signature | Throws `UnauthorizedException` |
| UT-AU-012 | `parseJwt` | Missing org_id claim | Throws `UnauthorizedException` |

---

## 4. Backend Integration Tests

### 4.1 Scope

Integration tests validate the interaction between application code and real infrastructure: databases, caches, and message brokers. They use Testcontainers to spin up disposable instances.

### 4.2 Framework & Configuration

```kotlin
// build.gradle.kts
dependencies {
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:junit-jupiter:1.19+")
    testImplementation("org.testcontainers:postgresql:1.19+")
    testImplementation("org.testcontainers:clickhouse:1.19+")
    testImplementation("org.testcontainers:kafka:1.19+")
    testImplementation("com.redis:testcontainers-redis:2.0+")
}
```

### 4.3 Shared Test Infrastructure

```java
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class BaseIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    static GenericContainer<?> clickhouse = new GenericContainer<>("clickhouse/clickhouse-server:latest")
        .withExposedPorts(8123);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7")
        .withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));
}
```

### 4.4 Test Cases by Module

#### 4.4.1 Repository Integration Tests

| Test ID | Repository | Scenario | Validates |
|---|---|---|---|
| IT-RP-001 | `AnalyticsRepository` | Query org summary from ClickHouse with seeded data | Correct aggregation (SUM, COUNT, AVG) |
| IT-RP-002 | `AnalyticsRepository` | Query timeseries with daily granularity | Correct daily bucketing, ordered by date |
| IT-RP-003 | `AnalyticsRepository` | Query by-team breakdown | Correct GROUP BY team, correct totals |
| IT-RP-004 | `AnalyticsRepository` | Query by-agent-type breakdown | Correct GROUP BY agent_type |
| IT-RP-005 | `AnalyticsRepository` | Query top users sorted by cost | Correct ORDER BY, LIMIT |
| IT-RP-006 | `AnalyticsRepository` | Query with all filters combined | All WHERE clauses applied correctly |
| IT-RP-007 | `AnalyticsRepository` | Query across org boundary | Returns empty — no cross-org leakage |
| IT-RP-008 | `AgentRunRepository` | Find run by ID in PostgreSQL | Correct entity mapping |
| IT-RP-009 | `AgentRunRepository` | List runs by user with cursor pagination | Correct ordering, cursor semantics |
| IT-RP-010 | `BudgetRepository` | CRUD operations on budgets table | Insert, read, update, delete succeed |
| IT-RP-011 | `BudgetRepository` | Unique constraint on (org_id, scope, scope_id) | Duplicate insert throws exception |
| IT-RP-012 | `ExportRepository` | Create and update export record | Status transitions PROCESSING -> COMPLETED |
| IT-RP-013 | `OrganizationRepository` | Find org by external_id | Correct lookup |
| IT-RP-014 | `TeamRepository` | List teams by org_id | Returns only teams for that org |

#### 4.4.2 API Endpoint Integration Tests

| Test ID | Endpoint | Scenario | Expected |
|---|---|---|---|
| IT-EP-001 | `GET /api/v1/orgs/{orgId}/analytics/summary` | ORG_ADMIN with valid JWT | 200 with aggregate metrics |
| IT-EP-002 | `GET /api/v1/orgs/{orgId}/analytics/summary` | MEMBER role | 403 Forbidden |
| IT-EP-003 | `GET /api/v1/orgs/{orgId}/analytics/summary` | No JWT | 401 Unauthorized |
| IT-EP-004 | `GET /api/v1/orgs/{orgId}/analytics/summary` | Invalid date range | 400 Bad Request (RFC 7807) |
| IT-EP-005 | `GET /api/v1/orgs/{orgId}/analytics/summary` | Wrong org_id (not user's org) | 404 Not Found |
| IT-EP-006 | `GET /api/v1/orgs/{orgId}/analytics/timeseries` | Valid request, daily granularity | 200 with data_points array |
| IT-EP-007 | `GET /api/v1/orgs/{orgId}/analytics/by-team` | ORG_ADMIN, seeded data | 200 with team breakdown |
| IT-EP-008 | `GET /api/v1/orgs/{orgId}/analytics/by-agent-type` | ORG_ADMIN | 200 with agent type breakdown |
| IT-EP-009 | `GET /api/v1/orgs/{orgId}/analytics/top-users` | sort_by=cost | 200 with users sorted by cost |
| IT-EP-010 | `GET /api/v1/teams/{teamId}/analytics/summary` | TEAM_LEAD, own team | 200 with team metrics |
| IT-EP-011 | `GET /api/v1/teams/{teamId}/analytics/summary` | TEAM_LEAD, other team | 403 Forbidden |
| IT-EP-012 | `GET /api/v1/teams/{teamId}/analytics/by-user` | Valid team | 200 with per-user breakdown |
| IT-EP-013 | `GET /api/v1/users/me/analytics/summary` | Any authenticated user | 200 with personal metrics |
| IT-EP-014 | `GET /api/v1/users/me/runs` | Valid cursor pagination | 200 with runs + next_cursor |
| IT-EP-015 | `GET /api/v1/runs/{runId}` | Run owner | 200 with full run detail |
| IT-EP-016 | `GET /api/v1/runs/{runId}` | Non-owner, non-admin | 403 Forbidden |
| IT-EP-017 | `GET /api/v1/orgs/{orgId}/budgets` | ORG_ADMIN | 200 with budgets list |
| IT-EP-018 | `PUT /api/v1/orgs/{orgId}/budgets/{id}` | Valid budget creation | 200 with created budget |
| IT-EP-019 | `PUT /api/v1/orgs/{orgId}/budgets/{id}` | Invalid monthly_limit | 400 Bad Request |
| IT-EP-020 | `DELETE /api/v1/orgs/{orgId}/budgets/{id}` | Existing budget | 204 No Content |
| IT-EP-021 | `POST /api/v1/orgs/{orgId}/exports` | Valid export request | 202 Accepted with export_id |
| IT-EP-022 | `GET /api/v1/orgs/{orgId}/exports/{id}` | Completed export | 200 with download_url |
| IT-EP-023 | `GET /api/v1/orgs/{orgId}/teams` | Valid org | 200 with teams list |
| IT-EP-024 | `GET /api/v1/orgs/{orgId}/agent-types` | Valid org | 200 with agent types list |
| IT-EP-025 | Any endpoint | Rate limit exceeded (>100 req/min) | 429 with Retry-After header |

#### 4.4.3 Kafka Ingestion Integration Tests

| Test ID | Scenario | Validates |
|---|---|---|
| IT-KA-001 | Publish RUN_COMPLETED event to topic | Record appears in both PG and ClickHouse |
| IT-KA-002 | Publish duplicate event (same run_id) | Upsert, no duplicate records |
| IT-KA-003 | Publish event with unknown org_id | Event discarded, logged as warning |
| IT-KA-004 | Publish malformed event (invalid JSON) | Sent to dead-letter queue |
| IT-KA-005 | Publish 1000 events rapidly | All events processed, ClickHouse batch inserted |
| IT-KA-006 | Consumer restart after offset commit | No event loss, resumes from last offset |

#### 4.4.4 Cache Integration Tests

| Test ID | Scenario | Validates |
|---|---|---|
| IT-CA-001 | First request for analytics summary | Cache miss, query hits ClickHouse, result cached in Redis |
| IT-CA-002 | Second request within 5 min | Cache hit, no ClickHouse query |
| IT-CA-003 | Request after cache TTL (5 min) | Cache miss, fresh query to ClickHouse |
| IT-CA-004 | Manual cache invalidation (refresh) | Cache cleared, next request hits DB |
| IT-CA-005 | Cache key includes org_id | Different orgs get different cache entries |

---

## 5. Frontend Unit Tests

### 5.1 Scope

Frontend unit tests cover React components (rendering, interaction), custom hooks, utility functions, and API service modules.

### 5.2 Framework & Configuration

```typescript
// vitest.config.ts
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov', 'html'],
      thresholds: {
        lines: 70,
        branches: 65,
        functions: 70,
        statements: 70,
      },
    },
  },
});
```

```typescript
// src/test/setup.ts
import '@testing-library/jest-dom';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';
import { server } from './mocks/server';

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => { cleanup(); server.resetHandlers(); });
afterAll(() => server.close());
```

### 5.3 Naming Convention

Test files: `{ComponentName}.test.tsx` or `{module}.test.ts`
Test blocks: `describe('{ComponentName}')` with `it('should ...')` descriptions.

### 5.4 MSW (Mock Service Worker) for API Mocking

All frontend tests mock backend API calls using MSW to provide deterministic, network-independent test behavior.

```typescript
// src/test/mocks/handlers.ts
import { http, HttpResponse } from 'msw';

export const handlers = [
  http.get('/api/v1/orgs/:orgId/analytics/summary', ({ params }) => {
    return HttpResponse.json({
      org_id: params.orgId,
      total_runs: 142857,
      succeeded_runs: 135000,
      // ... full response
    });
  }),
  // ... handlers for all endpoints
];
```

### 5.5 Test Cases by Component

#### 5.5.1 Shared Components

| Test ID | Component | Scenario | Expected |
|---|---|---|---|
| FE-SC-001 | `DateRangeSelector` | Renders with default "Last 30 days" | Shows correct from/to dates |
| FE-SC-002 | `DateRangeSelector` | Click "Last 7 days" preset | Updates URL params, emits correct dates |
| FE-SC-003 | `DateRangeSelector` | Select custom range | Calendar popover opens, selection updates state |
| FE-SC-004 | `DateRangeSelector` | Range exceeds 1 year | Shows validation error |
| FE-SC-005 | `FilterBar` | Renders with team and agent type options | Dropdowns populated from API |
| FE-SC-006 | `FilterBar` | Select a team filter | URL params updated, data refetches |
| FE-SC-007 | `FilterBar` | Click "Clear filters" | All selections reset, URL params cleared |
| FE-SC-008 | `MetricCard` | Render with number variant | Displays formatted number (e.g., "142,857") |
| FE-SC-009 | `MetricCard` | Render with currency variant | Displays formatted currency (e.g., "$24,350.12") |
| FE-SC-010 | `MetricCard` | Render with comparison | Shows "+12%" or "-5%" badge |
| FE-SC-011 | `MetricCard` | Render in loading state | Shows skeleton loader |
| FE-SC-012 | `TimeseriesChart` | Render with data points | SVG line chart rendered with correct data |
| FE-SC-013 | `TimeseriesChart` | Hover over data point | Tooltip displays formatted values |
| FE-SC-014 | `TimeseriesChart` | Toggle between metrics | Chart updates to show selected metric |
| FE-SC-015 | `TimeseriesChart` | Empty data | Shows "No data" placeholder |
| FE-SC-016 | `DataTable` | Render with rows | All rows and columns rendered |
| FE-SC-017 | `DataTable` | Click sortable column header | Rows re-sorted by that column |
| FE-SC-018 | `DataTable` | Toggle column visibility | Column hidden/shown |
| FE-SC-019 | `DataTable` | Click row | Row click handler fires with row data |
| FE-SC-020 | `DataTable` | Loading state | Skeleton rows displayed |
| FE-SC-021 | `BudgetGauge` | Render at 45% utilization | Green color zone |
| FE-SC-022 | `BudgetGauge` | Render at 65% utilization | Yellow color zone |
| FE-SC-023 | `BudgetGauge` | Render at 95% utilization | Red color zone |
| FE-SC-024 | `BudgetGauge` | Render at 110% (over budget) | Red zone, displays over-budget indicator |
| FE-SC-025 | `ExportButton` | Click triggers export | POST request sent, polling begins |
| FE-SC-026 | `ExportButton` | Export completes | Download initiated |
| FE-SC-027 | `ExportButton` | Export fails | Error toast displayed |
| FE-SC-028 | `LastUpdatedIndicator` | Render with recent timestamp | Shows "2 minutes ago" |
| FE-SC-029 | `LastUpdatedIndicator` | Click refresh | TanStack Query invalidation triggered |

#### 5.5.2 Page Components

| Test ID | Page | Scenario | Expected |
|---|---|---|---|
| FE-PG-001 | `OrgDashboard` | Render with data | All metric cards, chart, tables rendered |
| FE-PG-002 | `OrgDashboard` | Loading state | All children show skeleton states |
| FE-PG-003 | `OrgDashboard` | API error | Error banner with retry button |
| FE-PG-004 | `OrgDashboard` | Change date range | All queries refetch with new range |
| FE-PG-005 | `OrgDashboard` | Apply team filter | All sections filter by team |
| FE-PG-006 | `TeamDashboard` | Render for specific team | Team-scoped metrics displayed |
| FE-PG-007 | `TeamDashboard` | Switch team via selector | Data refetches for new team |
| FE-PG-008 | `TeamDashboard` | TEAM_LEAD access | Budget gauge visible for own team |
| FE-PG-009 | `PersonalDashboard` | Render with user data | Personal metrics, rank, recent runs shown |
| FE-PG-010 | `PersonalDashboard` | Click run row | Navigates to /runs/:runId |
| FE-PG-011 | `PersonalDashboard` | Click "View All" runs | Navigates to /me/runs |
| FE-PG-012 | `RunDetail` | Render succeeded run | Metadata, tokens, cost displayed; no error section |
| FE-PG-013 | `RunDetail` | Render failed run | Error category and message displayed |
| FE-PG-014 | `RunDetail` | Click back button | Navigates to previous page |
| FE-PG-015 | `BudgetSettings` | Render budget list | All budgets displayed in table |
| FE-PG-016 | `BudgetSettings` | Click "+ New Budget" | Modal opens with empty form |
| FE-PG-017 | `BudgetSettings` | Submit valid budget | Optimistic update, API call, table refreshes |
| FE-PG-018 | `BudgetSettings` | Submit invalid budget (negative limit) | Validation error shown |
| FE-PG-019 | `BudgetSettings` | Delete budget | Confirmation dialog, then removal |

#### 5.5.3 Routing & Authorization

| Test ID | Scenario | Expected |
|---|---|---|
| FE-RT-001 | ORG_ADMIN navigates to `/` | Redirected to `/org` |
| FE-RT-002 | TEAM_LEAD navigates to `/` | Redirected to `/teams/:defaultTeamId` |
| FE-RT-003 | MEMBER navigates to `/` | Redirected to `/me` |
| FE-RT-004 | MEMBER navigates to `/org` | Redirected to `/me` (unauthorized) |
| FE-RT-005 | TEAM_LEAD navigates to `/settings/budgets` | Redirected (unauthorized) |
| FE-RT-006 | Unauthenticated user | Redirected to OAuth login |
| FE-RT-007 | Deep link with query params | Params preserved after auth redirect |

#### 5.5.4 Custom Hooks

| Test ID | Hook | Scenario | Expected |
|---|---|---|---|
| FE-HK-001 | `useOrgAnalytics` | Successful fetch | Returns data, isLoading=false |
| FE-HK-002 | `useOrgAnalytics` | Network error | Returns error, isLoading=false |
| FE-HK-003 | `useOrgAnalytics` | Stale data (>5 min) | Triggers background refetch |
| FE-HK-004 | `useDateRange` | Initial load from URL params | Parses from/to from URL |
| FE-HK-005 | `useDateRange` | No URL params | Defaults to last 30 days |
| FE-HK-006 | `useFilters` | Apply and clear filters | URL params update correctly |
| FE-HK-007 | `useExport` | Polling during export | Status updates from PROCESSING to COMPLETED |
| FE-HK-008 | `useBudgetMutation` | Optimistic create | UI updates immediately, rolls back on error |

---

## 6. Contract Tests

### 6.1 Purpose

Contract tests verify that the API contract between frontend and backend remains stable. They catch breaking changes (removed fields, type changes, renamed endpoints) before they reach production.

### 6.2 Approach: Consumer-Driven Contracts with Pact

The frontend (consumer) defines expectations; the backend (provider) verifies it satisfies those expectations.

### 6.3 Contract Definitions

| Contract ID | Consumer Request | Expected Provider Response |
|---|---|---|
| CT-001 | `GET /api/v1/orgs/{orgId}/analytics/summary?from=...&to=...` | 200: body matches `OrgSummaryResponse` schema |
| CT-002 | `GET /api/v1/orgs/{orgId}/analytics/timeseries?from=...&to=...` | 200: body has `data_points` array with required fields |
| CT-003 | `GET /api/v1/orgs/{orgId}/analytics/by-team?from=...&to=...` | 200: body has `teams` array |
| CT-004 | `GET /api/v1/orgs/{orgId}/analytics/by-agent-type?from=...&to=...` | 200: body has `agent_types` array |
| CT-005 | `GET /api/v1/orgs/{orgId}/analytics/top-users?from=...&to=...&sort_by=cost` | 200: body has `users` array |
| CT-006 | `GET /api/v1/teams/{teamId}/analytics/summary?from=...&to=...` | 200: matches team summary schema |
| CT-007 | `GET /api/v1/teams/{teamId}/analytics/by-user?from=...&to=...` | 200: body has `users` array |
| CT-008 | `GET /api/v1/users/me/analytics/summary?from=...&to=...` | 200: body has `team_rank`, `team_size` |
| CT-009 | `GET /api/v1/users/me/runs?cursor=...&limit=50` | 200: body has `runs` array, `next_cursor`, `has_more` |
| CT-010 | `GET /api/v1/runs/{runId}` | 200: full run detail with all token/cost fields |
| CT-011 | `GET /api/v1/orgs/{orgId}/budgets` | 200: body has `budgets` array with utilization |
| CT-012 | `PUT /api/v1/orgs/{orgId}/budgets/{id}` | 200: returns created/updated budget |
| CT-013 | `POST /api/v1/orgs/{orgId}/exports` | 202: body has `export_id`, `status` |
| CT-014 | `GET /api/v1/orgs/{orgId}/exports/{id}` | 200: body has `status`, conditional `download_url` |
| CT-015 | Any endpoint with invalid JWT | 401: RFC 7807 error response |
| CT-016 | Any endpoint with insufficient role | 403: RFC 7807 error response |

---

## 7. E2E Acceptance Tests (Playwright)

### 7.1 Purpose

End-to-end acceptance tests validate complete user journeys through the running application. Each test maps directly to functional requirements from the product requirements document.

### 7.2 Framework & Configuration

```typescript
// playwright.config.ts
import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 4 : undefined,
  reporter: [
    ['html', { open: 'never' }],
    ['junit', { outputFile: 'e2e-results.xml' }],
  ],
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
    { name: 'webkit', use: { ...devices['Desktop Safari'] } },
    { name: 'mobile-chrome', use: { ...devices['Pixel 5'] } },
  ],
});
```

### 7.3 Test Fixtures

```typescript
// e2e/fixtures/auth.ts
import { test as base, Page } from '@playwright/test';

type AuthFixtures = {
  orgAdminPage: Page;
  teamLeadPage: Page;
  memberPage: Page;
};

export const test = base.extend<AuthFixtures>({
  orgAdminPage: async ({ browser }, use) => {
    const context = await browser.newContext({
      storageState: 'e2e/fixtures/org-admin-auth.json',
    });
    const page = await context.newPage();
    await use(page);
    await context.close();
  },
  teamLeadPage: async ({ browser }, use) => {
    const context = await browser.newContext({
      storageState: 'e2e/fixtures/team-lead-auth.json',
    });
    const page = await context.newPage();
    await use(page);
    await context.close();
  },
  memberPage: async ({ browser }, use) => {
    const context = await browser.newContext({
      storageState: 'e2e/fixtures/member-auth.json',
    });
    const page = await context.newPage();
    await use(page);
    await context.close();
  },
});
```

### 7.4 Test Data Seeding

Each E2E test suite uses a dedicated test data seed that is loaded before the suite runs and cleaned up after. Seeds are defined as SQL fixtures:

```
e2e/
├── fixtures/
│   ├── seed-org-dashboard.sql       # Org with teams, users, agent runs
│   ├── seed-budget-management.sql   # Org with budgets at various thresholds
│   ├── seed-export.sql              # Org with exportable data
│   └── seed-empty-org.sql           # Org with no agent runs
```

### 7.5 Acceptance Test Suites

#### 7.5.1 Suite: Organization Dashboard (Requirements ORG-1 through ORG-10)

| Test ID | Requirement | Scenario | Steps | Expected Result |
|---|---|---|---|---|
| AT-ORG-001 | ORG-1 | View aggregate KPIs | 1. Login as ORG_ADMIN 2. Navigate to /org 3. Set date range to "Last 30 days" | Metric cards show total runs, total tokens, total cost |
| AT-ORG-002 | ORG-2 | View time-series chart | 1. Navigate to /org 2. Observe chart area | Line chart displays daily runs/tokens/cost |
| AT-ORG-003 | ORG-3 | View team breakdown | 1. Navigate to /org 2. Scroll to "Usage by Team" | Table and bar chart show per-team data |
| AT-ORG-004 | ORG-4 | View agent type breakdown | 1. Navigate to /org 2. Scroll to "Usage by Agent Type" | Table and pie chart show per-agent-type data |
| AT-ORG-005 | ORG-5 | View top users | 1. Navigate to /org 2. Scroll to "Top Users" | Table shows top 10 users by run count |
| AT-ORG-006 | ORG-5 | Sort top users by cost | 1. Click "Cost" sort option | Table re-sorts by cost descending |
| AT-ORG-007 | ORG-6 | View success rate trend | 1. Navigate to /org 2. Select success rate metric on chart | Chart shows success rate over time |
| AT-ORG-008 | ORG-7 | View avg duration trend | 1. Navigate to /org 2. Select duration metric on chart | Chart shows avg duration over time |
| AT-ORG-009 | ORG-8 | Change date range to last 7 days | 1. Click "Last 7 days" preset | All data refreshes for 7-day window |
| AT-ORG-010 | ORG-8 | Select custom date range | 1. Open custom range picker 2. Select Jan 1-Jan 15 | Data refreshes for custom range |
| AT-ORG-011 | ORG-8 | Attempt range > 1 year | 1. Select range spanning > 365 days | Validation error shown |
| AT-ORG-012 | ORG-9 | Filter by team | 1. Select "Platform" in team filter | All sections filter to Platform team |
| AT-ORG-013 | ORG-9 | Filter by agent type | 1. Select "Code Review" in agent type filter | All sections filter to Code Review |
| AT-ORG-014 | ORG-9 | Filter by status=FAILED | 1. Select "Failed" in status filter | Metrics show only failed runs |
| AT-ORG-015 | ORG-9 | Combine multiple filters | 1. Select team + agent type + status | All filters applied simultaneously |
| AT-ORG-016 | ORG-9 | Clear filters | 1. Apply filters 2. Click "Clear filters" | All filters removed, full data shown |
| AT-ORG-017 | ORG-10 | Export as CSV | 1. Click "Export" button 2. Wait for completion | CSV file downloads with correct data |

#### 7.5.2 Suite: Team Dashboard (Requirements TEAM-1 through TEAM-5)

| Test ID | Requirement | Scenario | Steps | Expected Result |
|---|---|---|---|---|
| AT-TM-001 | TEAM-1 | View team KPIs | 1. Login as TEAM_LEAD 2. Navigate to /teams/:teamId | Metric cards show team-scoped runs, tokens, cost |
| AT-TM-002 | TEAM-2 | View user breakdown | 1. Navigate to team dashboard 2. Scroll to "Usage by User" | Table shows per-user metrics |
| AT-TM-003 | TEAM-3 | View agent type breakdown | 1. Scroll to "Usage by Agent Type" | Table shows per-agent-type metrics |
| AT-TM-004 | TEAM-4 | Change date range | 1. Select "Last 90 days" | All data refreshes |
| AT-TM-005 | TEAM-4 | Apply agent type filter | 1. Select agent type in filter | Data filtered |
| AT-TM-006 | TEAM-5 | Switch between teams | 1. Click team selector 2. Choose different team | Dashboard reloads with new team data |

#### 7.5.3 Suite: Personal Dashboard (Requirements USER-1 through USER-6)

| Test ID | Requirement | Scenario | Steps | Expected Result |
|---|---|---|---|---|
| AT-US-001 | USER-1 | View personal KPIs | 1. Login as MEMBER 2. Navigate to /me | Metric cards show user's runs, tokens, cost |
| AT-US-002 | USER-2 | View personal time-series | 1. Navigate to /me 2. Observe chart | Line chart shows user's daily usage |
| AT-US-003 | USER-3 | View recent runs table | 1. Scroll to "Recent Runs" | Table shows runs with all specified columns |
| AT-US-004 | USER-4 | View run details | 1. Click a run row | Navigate to /runs/:runId, shows full metadata |
| AT-US-005 | USER-4 | View failed run details | 1. Click a failed run | Error category and message displayed |
| AT-US-006 | USER-5 | Change date range | 1. Select "Last 7 days" | Personal data refreshes |
| AT-US-007 | USER-6 | View team rank | 1. Navigate to /me | Rank badge shows "You are #N of M engineers..." |

#### 7.5.4 Suite: Run Detail (Requirements RUN-1 through RUN-6)

| Test ID | Requirement | Scenario | Steps | Expected Result |
|---|---|---|---|---|
| AT-RN-001 | RUN-1 | View run metadata | 1. Navigate to /runs/:runId | Shows run ID, agent type, user, team, times, status |
| AT-RN-002 | RUN-2 | View token breakdown | 1. Navigate to /runs/:runId | Input, output, total tokens displayed |
| AT-RN-003 | RUN-3 | View cost breakdown | 1. Navigate to /runs/:runId | Input, output, total cost displayed |
| AT-RN-004 | RUN-4 | View model info | 1. Navigate to /runs/:runId | Model name and version displayed |
| AT-RN-005 | RUN-5 | View failed run error | 1. Navigate to /runs/:runId (failed) | Error category and message shown |
| AT-RN-006 | RUN-5 | Succeeded run hides error section | 1. Navigate to /runs/:runId (succeeded) | No error section visible |
| AT-RN-007 | RUN-6 | Owner accesses own run | 1. Login as run owner 2. Navigate to run | 200, full detail shown |
| AT-RN-008 | RUN-6 | Team lead accesses team member's run | 1. Login as TEAM_LEAD 2. Navigate to team member's run | 200, full detail shown |
| AT-RN-009 | RUN-6 | Non-authorized user accesses run | 1. Login as MEMBER 2. Navigate to another user's run | Access denied / 403 |

#### 7.5.5 Suite: Budget Management (Requirements ALERT-1 through ALERT-5)

| Test ID | Requirement | Scenario | Steps | Expected Result |
|---|---|---|---|---|
| AT-BG-001 | ALERT-1 | Create org budget | 1. Login as ORG_ADMIN 2. Go to /settings/budgets 3. Click "+ New Budget" 4. Set scope=Organization, limit=$50,000 5. Save | Budget appears in table |
| AT-BG-002 | ALERT-2 | Create team budget | 1. Click "+ New Budget" 2. Set scope=Team, select team, limit=$15,000 3. Save | Team budget appears in table |
| AT-BG-003 | ALERT-3 | Verify 50% threshold notification | 1. Seed data where spend = 51% of budget 2. Wait for budget checker | In-app notification received |
| AT-BG-004 | ALERT-3 | Verify 80% threshold notification | 1. Seed data where spend = 81% 2. Wait | Notification received |
| AT-BG-005 | ALERT-3 | Verify 100% threshold notification | 1. Seed data where spend = 101% 2. Wait | Notification received |
| AT-BG-006 | ALERT-4 | Configure custom thresholds | 1. Edit budget 2. Add 25% and 75% thresholds 3. Save | Custom thresholds saved and applied |
| AT-BG-007 | ALERT-5 | Budget gauge on org dashboard | 1. Navigate to /org | Budget gauge shows current utilization |
| AT-BG-008 | ALERT-5 | Budget gauge on team dashboard | 1. Navigate to /teams/:teamId | Team budget gauge shown |
| AT-BG-009 | — | Edit existing budget | 1. Click "Edit" on a budget 2. Change limit 3. Save | Budget updated in table |
| AT-BG-010 | — | Delete budget | 1. Click "Delete" 2. Confirm | Budget removed from table |

#### 7.5.6 Suite: Data Freshness (Requirements FRESH-1 through FRESH-4)

| Test ID | Requirement | Scenario | Steps | Expected Result |
|---|---|---|---|---|
| AT-FR-001 | FRESH-1 | Data within 5 min | 1. Ingest new event 2. Wait 3. Refresh dashboard | New data appears within 5 minutes |
| AT-FR-002 | FRESH-2 | Last updated indicator | 1. Navigate to any dashboard | "Last updated: X minutes ago" shown |
| AT-FR-003 | FRESH-3 | Manual refresh | 1. Click refresh button | Data reloads, timestamp updates |
| AT-FR-004 | FRESH-4 | Running agent indicator | 1. Seed a RUNNING agent run 2. Navigate to /me | Running indicator shown on that run |

#### 7.5.7 Suite: Authentication & Authorization (Requirements AUTH-1 through AUTH-6)

| Test ID | Requirement | Scenario | Steps | Expected Result |
|---|---|---|---|---|
| AT-AU-001 | AUTH-1 | SSO login flow | 1. Open dashboard without session 2. Complete OAuth flow | Redirected to dashboard with valid session |
| AT-AU-002 | AUTH-2 | Role-based navigation | 1. Login as each role | Default redirect matches role |
| AT-AU-003 | AUTH-3 | ORG_ADMIN full access | 1. Login as ORG_ADMIN 2. Visit all pages | All pages accessible |
| AT-AU-004 | AUTH-4 | TEAM_LEAD scoped access | 1. Login as TEAM_LEAD 2. Visit team pages | Own team pages accessible, other teams blocked |
| AT-AU-005 | AUTH-5 | MEMBER restricted access | 1. Login as MEMBER 2. Visit /org | Redirected to /me |
| AT-AU-006 | AUTH-5 | MEMBER sees anonymized aggregates | 1. Login as MEMBER 2. View any aggregate data | No PII from other users visible |
| AT-AU-007 | AUTH-6 | JWT roles correctly mapped | 1. Login 2. Verify role from JWT matches behavior | Navigation and data access match JWT roles |

#### 7.5.8 Suite: Cross-Cutting Concerns

| Test ID | Scenario | Steps | Expected Result |
|---|---|---|---|
| AT-CC-001 | URL state persistence | 1. Apply filters + date range 2. Copy URL 3. Open in new tab | Same view with same filters |
| AT-CC-002 | Browser back/forward | 1. Navigate between pages 2. Press back | Previous page restored with state |
| AT-CC-003 | Error recovery | 1. Simulate API error 2. Click "Retry" | Data loads successfully |
| AT-CC-004 | Responsive - tablet | 1. Resize to 1024px width | Two-column layout stacks correctly |
| AT-CC-005 | Responsive - mobile | 1. Resize to 375px width | Single column, metric cards scroll horizontally |
| AT-CC-006 | Keyboard navigation | 1. Tab through all interactive elements | All elements reachable, focus indicators visible |
| AT-CC-007 | Session expiry | 1. Wait for JWT to expire 2. Interact with dashboard | Redirected to re-authenticate |

---

## 8. Load & Performance Tests

### 8.1 Framework

Gatling (Java DSL) simulates concurrent users and validates latency targets.

### 8.2 Scenarios

| Test ID | Scenario | Concurrency | Duration | Success Criteria |
|---|---|---|---|---|
| LT-001 | Org dashboard page load | 500 concurrent users | 10 min | p95 < 2s, p99 < 5s, 0% errors |
| LT-002 | Org summary API | 1000 req/s | 10 min | p95 < 500ms, p99 < 2s |
| LT-003 | Timeseries API (90-day range) | 500 req/s | 10 min | p95 < 300ms |
| LT-004 | Top users API | 500 req/s | 10 min | p95 < 500ms |
| LT-005 | Personal runs list (paginated) | 1000 req/s | 10 min | p95 < 500ms |
| LT-006 | Run detail API | 1000 req/s | 10 min | p95 < 200ms |
| LT-007 | CSV export (100k rows) | 50 concurrent exports | 10 min | All complete < 30s |
| LT-008 | Kafka ingestion throughput | 50M events/day rate | 30 min | Lag < 30s, no consumer failures |
| LT-009 | Mixed workload | 5000 concurrent users | 30 min | p95 < 500ms across all endpoints |
| LT-010 | Cache cold start | Flush Redis, 1000 req/s | 5 min | p95 < 2s during warm-up, < 500ms after |

### 8.3 Load Test Data

- 100 organizations
- 10,000 users across orgs
- 50 million agent run records in ClickHouse
- Realistic distribution: 70% succeeded, 20% failed, 5% cancelled, 5% running

---

## 9. Security Testing

### 9.1 Scope

Security tests validate tenant isolation, authorization enforcement, and input validation.

| Test ID | Scenario | Expected |
|---|---|---|
| ST-001 | Org A user queries org B's data by changing orgId in URL | 404 Not Found (no data leakage) |
| ST-002 | Modify JWT org_id claim (tampered token) | 401 Unauthorized (signature invalid) |
| ST-003 | SQL injection in query parameters | No SQL execution, 400 Bad Request |
| ST-004 | XSS payload in agent type display name | Escaped in output, no script execution |
| ST-005 | CSRF on budget mutation | Rejected (JWT Bearer auth, no cookies) |
| ST-006 | Brute force rate limiting | 429 after 100 requests/minute per user |
| ST-007 | Access export download URL from different org | 403 or signed URL expired/invalid |
| ST-008 | Expired JWT | 401 Unauthorized |
| ST-009 | JWT with missing required claims | 401 Unauthorized |
| ST-010 | Path traversal in export file path | Sanitized, no file system access |

---

## 10. CI/CD Integration

### 10.1 Test Execution Order in Pipeline

```
Push to branch
  │
  ├── Stage 1: Static Analysis (parallel)
  │   ├── ESLint + TypeScript check (frontend)
  │   ├── Checkstyle + SpotBugs (backend)
  │   └── Pact contract validation
  │
  ├── Stage 2: Unit Tests (parallel)
  │   ├── Vitest (frontend, ~2 min)
  │   └── JUnit 5 (backend, ~3 min)
  │
  ├── Stage 3: Integration Tests
  │   └── Spring Boot Test + Testcontainers (~8 min)
  │
  ├── Stage 4: Build & Deploy to Staging
  │   ├── Docker build
  │   └── Helm deploy to staging namespace
  │
  ├── Stage 5: E2E Acceptance Tests
  │   └── Playwright against staging (~10 min)
  │
  ├── Stage 6: Load Tests (nightly / pre-release only)
  │   └── Gatling against staging (~30 min)
  │
  └── Stage 7: Manual Approval → Production Deploy
```

### 10.2 Quality Gates

| Gate | Criteria | Blocks |
|---|---|---|
| Static analysis | 0 SpotBugs high/critical, 0 ESLint errors | Merge to main |
| Unit test coverage | Backend >= 80%, Frontend >= 70% | Merge to main |
| Integration tests | 100% pass | Merge to main |
| Contract tests | 100% pass | Merge to main |
| E2E acceptance | 100% pass (with retries) | Deploy to production |
| Load tests | p95 within targets | Release approval |

### 10.3 Flaky Test Policy

1. Tests that fail intermittently are tagged `@Flaky` and investigated within 48 hours.
2. Flaky tests are excluded from blocking the pipeline but tracked on a dashboard.
3. A test that fails > 3 times in 7 days without a fix is either fixed or deleted.
4. Root cause categories tracked: timing, test data pollution, resource contention, network.

---

## 11. Test Data Management

### 11.1 Principles

1. **Isolation**: Each test suite uses its own dataset. No shared mutable state between tests.
2. **Determinism**: Test data is seeded explicitly. No reliance on production data or random generation for assertions.
3. **Cleanup**: Integration and E2E tests clean up after themselves (transaction rollback or explicit DELETE).

### 11.2 Test Data Builders (Backend)

```java
public class TestDataBuilders {

    public static AgentRun.Builder anAgentRun() {
        return AgentRun.builder()
            .id(UUID.randomUUID())
            .orgId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
            .teamId(UUID.fromString("00000000-0000-0000-0000-000000000010"))
            .userId(UUID.fromString("00000000-0000-0000-0000-000000000100"))
            .agentTypeSlug("code_review")
            .modelName("claude-sonnet-4")
            .status("SUCCEEDED")
            .startedAt(Instant.parse("2026-01-15T08:30:00Z"))
            .finishedAt(Instant.parse("2026-01-15T08:30:34Z"))
            .durationMs(34000L)
            .inputTokens(350000L)
            .outputTokens(130000L)
            .totalTokens(480000L)
            .inputCost(new BigDecimal("0.105000"))
            .outputCost(new BigDecimal("0.093000"))
            .totalCost(new BigDecimal("0.198000"));
    }

    public static Organization.Builder anOrganization() {
        return Organization.builder()
            .id(UUID.fromString("00000000-0000-0000-0000-000000000001"))
            .externalId("test-org-1")
            .name("Test Organization");
    }

    public static Budget.Builder aBudget() {
        return Budget.builder()
            .orgId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
            .scope("ORGANIZATION")
            .monthlyLimit(new BigDecimal("50000.000000"))
            .thresholds(List.of(0.5, 0.8, 1.0))
            .notificationChannels(List.of("IN_APP", "EMAIL"));
    }
}
```

### 11.3 Test Factories (Frontend)

```typescript
// src/test/factories.ts
import { faker } from '@faker-js/faker';

export function buildOrgSummary(overrides?: Partial<OrgSummary>): OrgSummary {
  return {
    org_id: faker.string.uuid(),
    period: { from: '2026-01-01', to: '2026-01-31' },
    total_runs: 142857,
    succeeded_runs: 135000,
    failed_runs: 7500,
    cancelled_runs: 357,
    running_runs: 0,
    success_rate: 0.9451,
    total_tokens: 58000000000,
    total_input_tokens: 42000000000,
    total_output_tokens: 16000000000,
    total_cost: '24350.120000',
    avg_duration_ms: 34500,
    p50_duration_ms: 28000,
    p95_duration_ms: 95000,
    p99_duration_ms: 180000,
    ...overrides,
  };
}

export function buildAgentRun(overrides?: Partial<AgentRun>): AgentRun {
  return {
    run_id: faker.string.uuid(),
    agent_type: 'code_review',
    agent_type_display_name: 'Code Review Agent',
    status: 'SUCCEEDED',
    started_at: '2026-01-15T08:30:00Z',
    finished_at: '2026-01-15T08:30:34Z',
    duration_ms: 34000,
    total_tokens: 480000,
    total_cost: '0.198000',
    ...overrides,
  };
}

export function buildBudget(overrides?: Partial<Budget>): Budget {
  return {
    budget_id: faker.string.uuid(),
    scope: 'ORGANIZATION',
    scope_id: faker.string.uuid(),
    monthly_limit: '50000.000000',
    current_spend: '24350.120000',
    utilization: 0.487,
    thresholds: [0.50, 0.80, 1.00],
    notification_channels: ['IN_APP', 'EMAIL'],
    ...overrides,
  };
}
```

---

## 12. Traceability Matrix

Maps every product requirement to its test coverage.

| Requirement ID | Description | Unit Tests | Integration Tests | E2E Tests |
|---|---|---|---|---|
| AUTH-1 | OAuth SSO authentication | UT-AU-009..012 | IT-EP-003 | AT-AU-001 |
| AUTH-2 | Three roles | UT-AU-001..008 | IT-EP-002 | AT-AU-002..007 |
| AUTH-3 | ORG_ADMIN views all org data | UT-AU-001 | IT-EP-001 | AT-AU-003 |
| AUTH-4 | TEAM_LEAD views own teams | UT-AU-002..003 | IT-EP-010..011 | AT-AU-004 |
| AUTH-5 | MEMBER views own data | UT-AU-004..005 | IT-EP-013 | AT-AU-005..006 |
| AUTH-6 | Roles from JWT claims | UT-AU-009..012 | IT-EP-001..003 | AT-AU-007 |
| ORG-1 | Aggregate KPIs | UT-AN-001..007 | IT-EP-001 | AT-ORG-001 |
| ORG-2 | Time-series chart | UT-AN-008..010 | IT-EP-006 | AT-ORG-002 |
| ORG-3 | Team breakdown | UT-AN-011 | IT-EP-007 | AT-ORG-003 |
| ORG-4 | Agent type breakdown | UT-AN-012 | IT-EP-008 | AT-ORG-004 |
| ORG-5 | Top users | UT-AN-013..014 | IT-EP-009 | AT-ORG-005..006 |
| ORG-6 | Success rate trend | UT-AN-008 | IT-EP-006 | AT-ORG-007 |
| ORG-7 | Avg duration trend | UT-AN-008 | IT-EP-006 | AT-ORG-008 |
| ORG-8 | Date range controls | UT-AN-005..006 | IT-EP-004 | AT-ORG-009..011 |
| ORG-9 | Filters | UT-AN-002..004 | IT-EP-001 | AT-ORG-012..016 |
| ORG-10 | CSV export | UT-EX-001..008 | IT-EP-021..022 | AT-ORG-017 |
| TEAM-1 | Team KPIs | UT-AN-015..016 | IT-EP-010 | AT-TM-001 |
| TEAM-2 | User breakdown | — | IT-EP-012 | AT-TM-002 |
| TEAM-3 | Agent type breakdown | — | IT-EP-010 | AT-TM-003 |
| TEAM-4 | Date range + filters | UT-AN-005..006 | IT-EP-010 | AT-TM-004..005 |
| TEAM-5 | Team switching | — | — | AT-TM-006 |
| USER-1 | Personal KPIs | UT-AN-017..018 | IT-EP-013 | AT-US-001 |
| USER-2 | Personal time-series | UT-AN-017 | IT-EP-013 | AT-US-002 |
| USER-3 | Recent runs table | — | IT-EP-014 | AT-US-003 |
| USER-4 | Run detail drill-down | — | IT-EP-015..016 | AT-US-004..005 |
| USER-5 | Date range controls | — | — | AT-US-006 |
| USER-6 | Team rank | UT-AN-017 | IT-EP-013 | AT-US-007 |
| RUN-1 | Run metadata | — | IT-EP-015 | AT-RN-001 |
| RUN-2 | Token breakdown | — | IT-EP-015 | AT-RN-002 |
| RUN-3 | Cost breakdown | — | IT-EP-015 | AT-RN-003 |
| RUN-4 | Model info | — | IT-EP-015 | AT-RN-004 |
| RUN-5 | Error display for failed runs | — | IT-EP-015 | AT-RN-005..006 |
| RUN-6 | Access control on run detail | UT-AU-006..008 | IT-EP-015..016 | AT-RN-007..009 |
| ALERT-1 | Org budget creation | UT-BG-002 | IT-EP-018 | AT-BG-001 |
| ALERT-2 | Team budget creation | UT-BG-003 | IT-EP-018 | AT-BG-002 |
| ALERT-3 | Threshold notifications | UT-BG-010..013 | — | AT-BG-003..005 |
| ALERT-4 | Custom thresholds | UT-BG-014 | — | AT-BG-006 |
| ALERT-5 | Budget gauge display | — | — | AT-BG-007..008 |
| FRESH-1 | Data < 5 min stale | — | IT-CA-001..003 | AT-FR-001 |
| FRESH-2 | Last updated indicator | — | — | AT-FR-002 |
| FRESH-3 | Manual refresh | — | IT-CA-004 | AT-FR-003 |
| FRESH-4 | Running indicator | — | — | AT-FR-004 |

---

## 13. Acceptance Criteria Definition Format

Every user story or feature must include acceptance criteria using the Given-When-Then format before development begins. This is required for the TDD workflow to function — acceptance criteria are the source from which tests are derived.

### 13.1 Template

```
### Feature: [Feature Name]

**As a** [persona],
**I want** [capability],
**So that** [benefit].

#### Acceptance Criteria:

**AC-1: [Criterion Name]**
- Given [precondition]
- When [action]
- Then [expected outcome]

**AC-2: [Criterion Name]**
- Given [precondition]
- When [action]
- Then [expected outcome]

#### Edge Cases:
- [Edge case 1]: [Expected behavior]
- [Edge case 2]: [Expected behavior]
```

### 13.2 Example: Organization Summary

```
### Feature: Organization Analytics Summary

**As an** Org Admin,
**I want** to see aggregate usage metrics for my organization,
**So that** I can monitor overall agent consumption and cost.

#### Acceptance Criteria:

**AC-1: Display aggregate metrics**
- Given I am logged in as ORG_ADMIN
- And my organization has agent run data in the selected date range
- When I navigate to the organization dashboard
- Then I see metric cards showing: total runs, succeeded runs, failed runs,
  success rate, total tokens, total cost, average duration

**AC-2: Date range filtering**
- Given I am on the organization dashboard
- When I select "Last 7 days" from the date range selector
- Then all metrics refresh to reflect only the last 7 days of data

**AC-3: Empty state**
- Given I am logged in as ORG_ADMIN
- And my organization has no agent run data in the selected date range
- When I navigate to the organization dashboard
- Then I see metric cards with zero values
- And the chart shows a "No data for this period" message

**AC-4: Cross-filter interaction**
- Given I am on the organization dashboard
- When I select a team from the filter bar
- Then all sections (KPIs, chart, breakdowns, top users) filter to that team

#### Edge Cases:
- Date range of exactly 1 day: Show 24 hourly data points or 1 daily point
- Organization with 0 teams: Team breakdown section shows empty state
- All runs failed in range: Success rate shows 0%, cost still calculated
```

---

## 14. Definition of Done

A feature is considered done when:

- [ ] All acceptance criteria have corresponding automated tests
- [ ] Unit tests written (TDD red-green-refactor) and passing
- [ ] Integration tests passing
- [ ] Contract tests updated and passing
- [ ] E2E acceptance tests passing
- [ ] Code coverage meets thresholds (backend >= 80%, frontend >= 70%)
- [ ] No SpotBugs high/critical findings
- [ ] No ESLint errors
- [ ] TypeScript compiles with zero errors
- [ ] Reviewed and approved by at least one team member
- [ ] Deployed to staging and manually verified
- [ ] Performance within NFR targets (verified by load test for significant features)
