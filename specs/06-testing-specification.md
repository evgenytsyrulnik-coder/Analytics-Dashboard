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
Test methods: `{expectedBehavior}()` or nested classes grouping tests per method under test.

```java
class AnalyticsServiceTest {
    @Nested class GetOrgSummary {
        void returnsCorrectSummaryForMultipleRuns() {}
        void returnsZerosForEmptyRunList() {}
        void calculatesSuccessRateCorrectly() {}
        void passesFiltersToRepository() {}
    }
    @Nested class GetTeamSummary {
        void returnsCorrectSummaryForTeam() {}
        void throwsWhenTeamNotFound() {}
    }
}
```

### 3.4 Test Cases by Module

#### 3.4.1 Analytics Service (`AnalyticsServiceTest`)

**Implemented — 56 test methods across 13 nested test classes.**

##### GetOrgSummary (9 tests)

| Test ID | Scenario | Expected Result |
|---|---|---|
| UT-AN-001 | Multiple runs with various statuses | Returns correct aggregate metrics (total, succeeded, failed, cancelled, running) |
| UT-AN-002 | Empty run list | Returns zero counts for all metrics |
| UT-AN-003 | Mix of succeeded and failed runs | Calculates success rate correctly |
| UT-AN-004 | Runs with varying durations | Calculates P50, P95, P99 duration percentiles correctly |
| UT-AN-005 | team_id, agent_type, status filters provided | Passes all filters to repository query |
| UT-AN-006 | Runs with varying costs | Aggregates cost with correct scale (6 decimal places) |
| UT-AN-007 | Runs with all status types | Counts cancelled and running statuses separately |
| UT-AN-008 | Runs with null duration | Handles null duration gracefully (excluded from percentiles) |
| UT-AN-009 | Runs with separate input/output tokens | Separates input and output token aggregation |

##### GetTeamSummary (3 tests)

| Test ID | Scenario | Expected Result |
|---|---|---|
| UT-AN-010 | Valid team with runs | Returns correct summary for team |
| UT-AN-011 | Non-existent team ID | Throws `NoSuchElementException` |
| UT-AN-012 | Team summary with filters | Passes filters to team-scoped repository query |

##### GetUserSummary (3 tests)

| Test ID | Scenario | Expected Result |
|---|---|---|
| UT-AN-013 | User with runs in org | Returns correct user summary with metrics |
| UT-AN-014 | User with team members | Calculates team rank correctly |
| UT-AN-015 | User with no runs | Returns zero counts, handles empty state |

##### GetOrgTimeseries (5 tests)

| Test ID | Scenario | Expected Result |
|---|---|---|
| UT-AN-016 | Daily granularity, multiple days | Groups runs by day correctly |
| UT-AN-017 | No runs in range | Returns empty data points array |
| UT-AN-018 | Custom granularity parameter | Uses specified granularity for bucketing |
| UT-AN-019 | No granularity parameter | Defaults to daily granularity |
| UT-AN-020 | Multiple data points | Data points sorted by date ascending |

##### GetTeamTimeseries (1 test)

| Test ID | Scenario | Expected Result |
|---|---|---|
| UT-AN-021 | Valid team with runs | Returns timeseries scoped to team |

##### GetUserTimeseries (1 test)

| Test ID | Scenario | Expected Result |
|---|---|---|
| UT-AN-022 | Valid user with runs | Returns timeseries scoped to user |

##### GetByTeam (4 tests)

| Test ID | Scenario | Expected Result |
|---|---|---|
| UT-AN-023 | Multiple teams with runs | Breaks down runs by team |
| UT-AN-024 | Teams with mixed success/failure | Calculates success rate per team |
| UT-AN-025 | Runs with null team_id | Filters out runs without team assignment |
| UT-AN-026 | Unknown team ID in run data | Handles gracefully with "Unknown" label |

##### GetByAgentType (3 tests)

| Test ID | Scenario | Expected Result |
|---|---|---|
| UT-AN-027 | Multiple agent types | Breaks down runs by agent type |
| UT-AN-028 | Unknown agent type slug | Handles with fallback display name |
| UT-AN-029 | Multiple agent types | Sorts by total runs descending |

##### GetTopUsers (5 tests)

| Test ID | Scenario | Expected Result |
|---|---|---|
| UT-AN-030 | sort_by=runs | Returns top users sorted by run count descending |
| UT-AN-031 | sort_by=tokens | Returns top users sorted by token usage |
| UT-AN-032 | limit=5 | Respects limit parameter |
| UT-AN-033 | No sort_by parameter | Defaults sort to runs |
| UT-AN-034 | Unknown user ID in run data | Handles with "Unknown" display name |

##### GetTeamByUser (2 tests)

| Test ID | Scenario | Expected Result |
|---|---|---|
| UT-AN-035 | Team with multiple users | Breaks down team runs by individual user |
| UT-AN-036 | Non-existent team ID | Throws `NoSuchElementException` |

##### GetUserRuns (4 tests)

| Test ID | Scenario | Expected Result |
|---|---|---|
| UT-AN-037 | User with multiple runs | Returns run list respecting limit |
| UT-AN-038 | All runs returned (count <= limit) | `hasMore` is false |
| UT-AN-039 | Run entity mapping | Maps all run fields correctly (ID, type, status, timestamps, tokens, cost) |
| UT-AN-040 | User with no runs | Returns empty list |

##### GetRunDetail (5 tests)

| Test ID | Scenario | Expected Result |
|---|---|---|
| UT-AN-041 | Valid run ID | Returns detailed run information including model, tokens, cost |
| UT-AN-042 | Non-existent run ID | Throws `NoSuchElementException` |
| UT-AN-043 | Unknown agent type in run | Falls back to slug as display name |
| UT-AN-044 | Run with null finishedAt/duration | Handles in-progress run gracefully |
| UT-AN-045 | Failed run with error info | Includes error category and message |

##### GetOrgRuns (7 tests)

| Test ID | Scenario | Expected Result |
|---|---|---|
| UT-AN-046 | Org with paged runs | Returns paged runs with correct mapping (user name, team name) |
| UT-AN-047 | No runs in org | Returns empty page |
| UT-AN-048 | Status filter provided | Passes status filter to repository correctly |
| UT-AN-049 | All filters (team, user, status, agent_type) | Passes all filters to repository |
| UT-AN-050 | Runs with unknown user/team IDs | Handles with "Unknown" labels |
| UT-AN-051 | Run with null team_id | Handles null team gracefully |
| UT-AN-052 | Run with null finishedAt/duration | Handles in-progress runs in list view |

#### 3.4.2 Budget Service *(planned — not yet implemented)*

Budget mutation endpoints (`PUT`, `DELETE`) are not yet implemented. The following test cases will be added when the budget management service is built:

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

#### 3.4.3 Export Service *(planned — not yet implemented)*

Export endpoints are not yet implemented. The following test cases will be added when the export service is built:

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

#### 3.4.4 Ingestion Service *(planned — not yet implemented)*

The Kafka ingestion pipeline uses an emulator (`KafkaEventEmulator`) for local development. Full ingestion service tests will be added when the production Kafka consumer is built:

| Test ID | Method Under Test | Scenario | Expected Result |
|---|---|---|---|
| UT-IG-001 | `processEvent` | RUN_COMPLETED event | Writes to both PG and ClickHouse |
| UT-IG-002 | `processEvent` | RUN_STARTED event | Creates RUNNING record in PG |
| UT-IG-003 | `processEvent` | Duplicate run_id | Upserts (idempotent), no duplicate |
| UT-IG-004 | `processEvent` | Missing required fields | Logs error, sends to DLQ |
| UT-IG-005 | `processEvent` | Unknown org_id | Logs warning, discards event |
| UT-IG-006 | `batchInsert` | 1000 events batch | Flushes batch to ClickHouse |
| UT-IG-007 | `batchInsert` | Timeout (1 second) with 500 events | Flushes partial batch |

#### 3.4.5 Auth Configuration

**Implemented — 37 test methods across 4 test classes covering JWT handling, security context, auth filter, and error handling.**

##### JwtUtilTest (11 tests)

| Test ID | Method Under Test | Scenario | Expected Result |
|---|---|---|---|
| UT-AU-001 | `generateToken` | Valid user credentials | Returns non-null JWT string |
| UT-AU-002 | `parseToken` | Valid JWT | Returns valid Claims object |
| UT-AU-003 | `getUserId` | Token with sub claim | Extracts correct user UUID |
| UT-AU-004 | `getOrgId` | Token with org_id claim | Extracts correct org UUID |
| UT-AU-005 | `getRoles` | Token with roles claim | Extracts correct role list |
| UT-AU-006 | `getTeams` | Token with teams claim | Extracts correct team ID list |
| UT-AU-007 | `parseToken` | Invalid token string | Throws exception |
| UT-AU-008 | `parseToken` | Token signed with different secret | Throws SignatureException |
| UT-AU-009 | `parseToken` | Expired token | Throws ExpiredJwtException |
| UT-AU-010 | `generateToken` | Empty team list | Generates valid token with empty teams |
| UT-AU-011 | `generateToken` | Any valid input | Token contains iat and exp claims |

##### AuthContextTest (12 tests)

| Test ID | Method Under Test | Scenario | Expected Result |
|---|---|---|---|
| UT-AU-012 | `getUserId` | Valid security context | Delegates to JwtUtil, returns correct user ID |
| UT-AU-013 | `getOrgId` | Valid security context | Delegates to JwtUtil, returns correct org ID |
| UT-AU-014 | `getRoles` | Valid security context | Delegates to JwtUtil, returns correct roles |
| UT-AU-015 | `getTeamIds` | Token with team UUID strings | Parses string team IDs into UUID list |
| UT-AU-016 | `isOrgAdmin` | User with ORG_ADMIN role | Returns true |
| UT-AU-017 | `isOrgAdmin` | User without ORG_ADMIN role | Returns false |
| UT-AU-018 | `isTeamLead` | User with TEAM_LEAD role | Returns true |
| UT-AU-019 | `isTeamLead` | User with MEMBER role | Returns false |
| UT-AU-020 | `hasTeamAccess` | ORG_ADMIN accessing any team | Returns true (org admin bypass) |
| UT-AU-021 | `hasTeamAccess` | User belongs to the team | Returns true |
| UT-AU-022 | `hasTeamAccess` | User does not belong to the team | Returns false |
| UT-AU-023 | `hasTeamAccess` | MEMBER with no team assignments | Returns false |

##### JwtAuthFilterTest (7 tests)

| Test ID | Method Under Test | Scenario | Expected Result |
|---|---|---|---|
| UT-AU-024 | `doFilterInternal` | Valid Bearer token in header | Sets authentication in security context |
| UT-AU-025 | `doFilterInternal` | Token with multiple roles | Sets multiple authorities on authentication |
| UT-AU-026 | `doFilterInternal` | No Authorization header | Continues filter chain without auth |
| UT-AU-027 | `doFilterInternal` | Non-Bearer auth header | Continues filter chain without auth |
| UT-AU-028 | `doFilterInternal` | Invalid/malformed token | Continues filter chain without auth |
| UT-AU-029 | `doFilterInternal` | Bearer prefix extraction | Extracts token after "Bearer " prefix |
| UT-AU-030 | `doFilterInternal` | Role authority mapping | Prefixes roles with "ROLE_" for Spring Security |

##### GlobalExceptionHandlerTest (7 tests)

| Test ID | Method Under Test | Scenario | Expected Result |
|---|---|---|---|
| UT-AU-031 | `handleNotFound` | `NoSuchElementException` thrown | Returns 404 with RFC 7807 body |
| UT-AU-032 | `handleForbidden` | `AccessDeniedException` thrown | Returns 403 with RFC 7807 body |
| UT-AU-033 | `handleSecurityException` | `SecurityException` thrown | Returns 403 with RFC 7807 body |
| UT-AU-034 | `handleResponseStatus` | `ResponseStatusException` with custom status | Returns matching HTTP status code |
| UT-AU-035 | `handleResponseStatus` | `ResponseStatusException` with null reason | Handles null reason gracefully |
| UT-AU-036 | `handleBadRequest` | `IllegalArgumentException` thrown | Returns 400 with RFC 7807 body |
| UT-AU-037 | All handlers | Any exception | All responses contain `type` field |

### 3.5 Backend Controller Unit Tests

**Implemented — 71 test methods across 4 controller test classes using `@WebMvcTest` with mocked service layer.**

Controller tests validate HTTP request/response handling, authorization enforcement, parameter binding, and correct delegation to the service layer. Each controller is tested in isolation using `MockMvc` with `@MockBean` for dependencies.

#### 3.5.1 AuthControllerTest (7 tests)

| Test ID | Endpoint | Scenario | Expected |
|---|---|---|---|
| UT-CT-001 | `POST /api/v1/auth/login` | Valid email and password | 200 with JWT token, userId, orgId, orgName, email, displayName, role, teams |
| UT-CT-002 | `POST /api/v1/auth/login` | Wrong password | 401 Unauthorized |
| UT-CT-003 | `POST /api/v1/auth/login` | Non-existent email | 401 Unauthorized |
| UT-CT-004 | `POST /api/v1/auth/login` | Failed login | Response contains error details |
| UT-CT-005 | `POST /api/v1/auth/login` | User with team memberships | Response includes team info (teamId, teamName) |
| UT-CT-006 | `POST /api/v1/auth/login` | Any login attempt | Calls password encoder with correct arguments |
| UT-CT-007 | `POST /api/v1/auth/login` | User with multiple teams | Generates token with correct team IDs |

#### 3.5.2 OrgAnalyticsControllerTest (25 tests)

| Test ID | Endpoint | Scenario | Expected |
|---|---|---|---|
| UT-CT-008 | `GET /orgs/{orgId}/analytics/summary` | ORG_ADMIN, valid request | 200 with summary response |
| UT-CT-009 | `GET /orgs/{orgId}/analytics/summary` | All filters provided (team, agent type, status) | Passes all filters to service |
| UT-CT-010 | `GET /orgs/{orgId}/analytics/summary` | User's org != path orgId | Throws SecurityException |
| UT-CT-011 | `GET /orgs/{orgId}/analytics/timeseries` | Valid request | 200 with timeseries response |
| UT-CT-012 | `GET /orgs/{orgId}/analytics/timeseries` | Wrong org | Throws SecurityException |
| UT-CT-013 | `GET /orgs/{orgId}/analytics/by-team` | Valid request | 200 with team breakdown |
| UT-CT-014 | `GET /orgs/{orgId}/analytics/by-agent-type` | Valid request | 200 with agent type breakdown |
| UT-CT-015 | `GET /orgs/{orgId}/analytics/top-users` | Valid request | 200 with top users |
| UT-CT-016 | `GET /orgs/{orgId}/analytics/top-users` | limit > 50 | Caps limit at 50 |
| UT-CT-017 | `GET /orgs/{orgId}/runs` | Valid request | 200 with paged runs |
| UT-CT-018 | `GET /orgs/{orgId}/runs` | All filters (team, user, status, agent_type) | Passes all filters to service |
| UT-CT-019 | `GET /orgs/{orgId}/runs` | size > 100 | Caps size at 100 |
| UT-CT-020 | `GET /orgs/{orgId}/runs` | Wrong org | Throws SecurityException |
| UT-CT-021 | `GET /orgs/{orgId}/users` | ORG_ADMIN | 200 with user list |
| UT-CT-022 | `GET /orgs/{orgId}/users` | No users in org | 200 with empty list |
| UT-CT-023 | `GET /orgs/{orgId}/users` | Valid response | Contains userId, displayName, email fields |
| UT-CT-024 | `GET /orgs/{orgId}/users` | Wrong org | Throws SecurityException |
| UT-CT-025 | `GET /orgs/{orgId}/teams` | Valid request | 200 with teams list |
| UT-CT-026 | `GET /orgs/{orgId}/teams` | Wrong org | Throws SecurityException |
| UT-CT-027 | `GET /orgs/{orgId}/agent-types` | Valid request | 200 with agent types list |
| UT-CT-028 | `GET /orgs/{orgId}/budgets` | ORG_ADMIN | 200 with budgets list |
| UT-CT-029 | All org endpoints | User's org != path orgId | All reject with SecurityException |

#### 3.5.3 TeamAnalyticsControllerTest (7 tests)

| Test ID | Endpoint | Scenario | Expected |
|---|---|---|---|
| UT-CT-030 | `GET /teams/{teamId}/analytics/summary` | Authorized user (ORG_ADMIN or team member) | 200 with team summary |
| UT-CT-031 | `GET /teams/{teamId}/analytics/summary` | User without team access | Throws AccessDeniedException (403) |
| UT-CT-032 | `GET /teams/{teamId}/analytics/summary` | With filters (agent_type, status) | Passes filters to service |
| UT-CT-033 | `GET /teams/{teamId}/analytics/timeseries` | Authorized user | 200 with timeseries |
| UT-CT-034 | `GET /teams/{teamId}/analytics/timeseries` | Unauthorized user | Throws AccessDeniedException (403) |
| UT-CT-035 | `GET /teams/{teamId}/analytics/by-user` | Authorized user | 200 with user breakdown |
| UT-CT-036 | `GET /teams/{teamId}/analytics/by-user` | Unauthorized user | Throws AccessDeniedException (403) |

#### 3.5.4 UserAnalyticsControllerTest (32 tests)

| Test ID | Endpoint | Scenario | Expected |
|---|---|---|---|
| UT-CT-037 | `GET /users/me/analytics/summary` | Authenticated user | 200 with personal summary |
| UT-CT-038 | `GET /users/me/analytics/summary` | With filters | Passes filters to service |
| UT-CT-039 | `GET /users/me/analytics/summary` | Any authenticated user | Uses AuthContext for userId and orgId |
| UT-CT-040 | `GET /users/me/analytics/timeseries` | Authenticated user | 200 with personal timeseries |
| UT-CT-041 | `GET /users/me/runs` | Authenticated user | 200 with run list |
| UT-CT-042 | `GET /users/me/runs` | limit > 200 | Caps limit at 200 |
| UT-CT-043 | `GET /runs/{runId}` | Run owner | 200 with run detail |
| UT-CT-044 | `GET /runs/{runId}` | ORG_ADMIN (not owner) | 200 with run detail (admin access) |
| UT-CT-045 | `GET /runs/{runId}` | TEAM_LEAD with shared team | 200 with run detail (team lead access) |
| UT-CT-046 | `GET /runs/{runId}` | Non-owner, non-admin user | Throws AccessDeniedException (403) |
| UT-CT-047 | `GET /runs/{runId}` | TEAM_LEAD without team access | Throws AccessDeniedException (403) |
| UT-CT-048 | `GET /users/{userId}/analytics/summary` | ORG_ADMIN | 200 with target user's summary |
| UT-CT-049 | `GET /users/{userId}/analytics/summary` | TEAM_LEAD sharing team with target user | 200 with target user's summary |
| UT-CT-050 | `GET /users/{userId}/analytics/summary` | TEAM_LEAD not sharing team | Throws AccessDeniedException (403) |
| UT-CT-051 | `GET /users/{userId}/analytics/summary` | Target user in different org | Throws AccessDeniedException (403) |
| UT-CT-052 | `GET /users/{userId}/analytics/summary` | Non-existent user ID | Throws NotFoundException (404) |
| UT-CT-053 | `GET /users/{userId}/analytics/summary` | With filters | Passes filters to service |
| UT-CT-054 | `GET /users/{userId}/analytics/timeseries` | ORG_ADMIN | 200 with target user's timeseries |
| UT-CT-055 | `GET /users/{userId}/analytics/timeseries` | TEAM_LEAD sharing team | 200 with target user's timeseries |
| UT-CT-056 | `GET /users/{userId}/analytics/timeseries` | TEAM_LEAD not sharing team | Throws AccessDeniedException (403) |
| UT-CT-057 | `GET /users/{userId}/analytics/timeseries` | Non-existent user | Throws NotFoundException (404) |
| UT-CT-058 | `GET /users/{userId}/analytics/timeseries` | With filters | Passes filters to service |
| UT-CT-059 | `GET /users/{userId}/runs` | ORG_ADMIN | 200 with target user's runs |
| UT-CT-060 | `GET /users/{userId}/runs` | TEAM_LEAD sharing team | 200 with target user's runs |
| UT-CT-061 | `GET /users/{userId}/runs` | TEAM_LEAD not sharing team | Throws AccessDeniedException (403) |
| UT-CT-062 | `GET /users/{userId}/runs` | Non-existent user | Throws NotFoundException (404) |
| UT-CT-063 | `GET /users/{userId}/runs` | limit > 200 | Caps limit at 200 |
| UT-CT-064 | `GET /users/{userId}/runs` | With filters | Passes filters to service |

### 3.6 Backend Unit Test Summary

| Test Class | Test Count | Status |
|---|---|---|
| `AnalyticsServiceTest` | 56 | Implemented |
| `JwtUtilTest` | 11 | Implemented |
| `AuthContextTest` | 12 | Implemented |
| `JwtAuthFilterTest` | 7 | Implemented |
| `GlobalExceptionHandlerTest` | 7 | Implemented |
| `AuthControllerTest` | 7 | Implemented |
| `OrgAnalyticsControllerTest` | 25 | Implemented |
| `TeamAnalyticsControllerTest` | 7 | Implemented |
| `UserAnalyticsControllerTest` | 32 | Implemented |
| **Total** | **164** | |

---

## 4. Backend Integration Tests

### 4.1 Scope

Integration tests validate the interaction between application code and real infrastructure: databases, caches, and message brokers.

**Current state:** The application uses an H2 in-memory database with Spring Data JPA for development and testing. The test profile (`application-test.yml`) configures H2 with a test JWT secret and disables the Kafka event emulator. Full Testcontainers-based integration tests against PostgreSQL, ClickHouse, Redis, and Kafka are planned for the production deployment pipeline.

### 4.2 Framework & Configuration

**Current (H2-based integration):**

```yaml
# application-test.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
```

**Planned (Testcontainers for production pipeline):**

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

**Planned Testcontainers base class:**

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

##### Authentication

| Test ID | Endpoint | Scenario | Expected |
|---|---|---|---|
| IT-EP-001 | `POST /api/v1/auth/login` | Valid email and password | 200 with JWT token and user context (userId, orgId, orgName, role, teams) |
| IT-EP-002 | `POST /api/v1/auth/login` | Invalid password | 401 Unauthorized |
| IT-EP-003 | `POST /api/v1/auth/login` | Non-existent email | 401 Unauthorized |
| IT-EP-004 | Any protected endpoint | No JWT | 401 Unauthorized |
| IT-EP-005 | Any protected endpoint | Expired JWT | 401 Unauthorized |

##### Organization Analytics

| Test ID | Endpoint | Scenario | Expected |
|---|---|---|---|
| IT-EP-006 | `GET /api/v1/orgs/{orgId}/analytics/summary` | ORG_ADMIN with valid JWT | 200 with aggregate metrics |
| IT-EP-007 | `GET /api/v1/orgs/{orgId}/analytics/summary` | MEMBER role | 403 Forbidden |
| IT-EP-008 | `GET /api/v1/orgs/{orgId}/analytics/summary` | Wrong org_id (not user's org) | 403 Forbidden (SecurityException) |
| IT-EP-009 | `GET /api/v1/orgs/{orgId}/analytics/summary` | With all filters (team, agent_type, status) | 200 with filtered metrics |
| IT-EP-010 | `GET /api/v1/orgs/{orgId}/analytics/timeseries` | Valid request, daily granularity | 200 with dataPoints array |
| IT-EP-011 | `GET /api/v1/orgs/{orgId}/analytics/by-team` | ORG_ADMIN, seeded data | 200 with team breakdown |
| IT-EP-012 | `GET /api/v1/orgs/{orgId}/analytics/by-agent-type` | ORG_ADMIN | 200 with agent type breakdown |
| IT-EP-013 | `GET /api/v1/orgs/{orgId}/analytics/by-agent-type` | TEAM_LEAD | 200 (accessible to team leads) |
| IT-EP-014 | `GET /api/v1/orgs/{orgId}/analytics/top-users` | sort_by=cost | 200 with users sorted by cost |
| IT-EP-015 | `GET /api/v1/orgs/{orgId}/analytics/top-users` | limit=100 | Caps limit at 50 |

##### Organization Runs

| Test ID | Endpoint | Scenario | Expected |
|---|---|---|---|
| IT-EP-016 | `GET /api/v1/orgs/{orgId}/runs` | ORG_ADMIN, default pagination | 200 with paged runs (page 0, size 25) |
| IT-EP-017 | `GET /api/v1/orgs/{orgId}/runs` | With status filter (multi-value) | 200 with runs matching any selected status |
| IT-EP-018 | `GET /api/v1/orgs/{orgId}/runs` | With team_id + user_id filters | 200 with filtered runs |
| IT-EP-019 | `GET /api/v1/orgs/{orgId}/runs` | size > 100 | Caps size at 100 |
| IT-EP-020 | `GET /api/v1/orgs/{orgId}/runs` | Wrong org | 403 Forbidden |

##### Team Analytics

| Test ID | Endpoint | Scenario | Expected |
|---|---|---|---|
| IT-EP-021 | `GET /api/v1/teams/{teamId}/analytics/summary` | TEAM_LEAD, own team | 200 with team metrics |
| IT-EP-022 | `GET /api/v1/teams/{teamId}/analytics/summary` | TEAM_LEAD, other team | 403 Forbidden |
| IT-EP-023 | `GET /api/v1/teams/{teamId}/analytics/summary` | ORG_ADMIN, any team | 200 (admin bypass) |
| IT-EP-024 | `GET /api/v1/teams/{teamId}/analytics/timeseries` | Authorized user | 200 with timeseries |
| IT-EP-025 | `GET /api/v1/teams/{teamId}/analytics/by-user` | Valid team | 200 with per-user breakdown |

##### Personal & User Analytics

| Test ID | Endpoint | Scenario | Expected |
|---|---|---|---|
| IT-EP-026 | `GET /api/v1/users/me/analytics/summary` | Any authenticated user | 200 with personal metrics and team rank |
| IT-EP-027 | `GET /api/v1/users/me/analytics/timeseries` | Any authenticated user | 200 with personal timeseries |
| IT-EP-028 | `GET /api/v1/users/me/runs` | Valid limit parameter | 200 with runs + hasMore flag |
| IT-EP-029 | `GET /api/v1/users/me/runs` | limit > 200 | Caps limit at 200 |
| IT-EP-030 | `GET /api/v1/users/{userId}/analytics/summary` | ORG_ADMIN viewing any user | 200 with user's summary |
| IT-EP-031 | `GET /api/v1/users/{userId}/analytics/summary` | TEAM_LEAD sharing team | 200 with user's summary |
| IT-EP-032 | `GET /api/v1/users/{userId}/analytics/summary` | TEAM_LEAD not sharing team | 403 Forbidden |
| IT-EP-033 | `GET /api/v1/users/{userId}/analytics/summary` | User in different org | 403 Forbidden |
| IT-EP-034 | `GET /api/v1/users/{userId}/analytics/summary` | Non-existent user | 404 Not Found |
| IT-EP-035 | `GET /api/v1/users/{userId}/analytics/timeseries` | Authorized access | 200 with user's timeseries |
| IT-EP-036 | `GET /api/v1/users/{userId}/runs` | Authorized access | 200 with user's run list |

##### Run Detail

| Test ID | Endpoint | Scenario | Expected |
|---|---|---|---|
| IT-EP-037 | `GET /api/v1/runs/{runId}` | Run owner | 200 with full run detail (tokens, cost, model info) |
| IT-EP-038 | `GET /api/v1/runs/{runId}` | ORG_ADMIN (non-owner) | 200 with full run detail |
| IT-EP-039 | `GET /api/v1/runs/{runId}` | TEAM_LEAD with team access | 200 with full run detail |
| IT-EP-040 | `GET /api/v1/runs/{runId}` | Non-owner, non-admin, no team access | 403 Forbidden |
| IT-EP-041 | `GET /api/v1/runs/{runId}` | Non-existent run ID | 404 Not Found |

##### Reference Data & Budgets

| Test ID | Endpoint | Scenario | Expected |
|---|---|---|---|
| IT-EP-042 | `GET /api/v1/orgs/{orgId}/teams` | Any authenticated user in org | 200 with teams list |
| IT-EP-043 | `GET /api/v1/orgs/{orgId}/agent-types` | Any authenticated user in org | 200 with agent types list |
| IT-EP-044 | `GET /api/v1/orgs/{orgId}/users` | ORG_ADMIN | 200 with users list (userId, displayName, email) |
| IT-EP-045 | `GET /api/v1/orgs/{orgId}/users` | Wrong org | 403 Forbidden |
| IT-EP-046 | `GET /api/v1/orgs/{orgId}/budgets` | ORG_ADMIN | 200 with budgets list |
| IT-EP-047 | `PUT /api/v1/orgs/{orgId}/budgets/{id}` | Valid budget creation *(planned)* | 200 with created budget |
| IT-EP-048 | `DELETE /api/v1/orgs/{orgId}/budgets/{id}` | Existing budget *(planned)* | 204 No Content |

##### Export *(planned)*

| Test ID | Endpoint | Scenario | Expected |
|---|---|---|---|
| IT-EP-049 | `POST /api/v1/orgs/{orgId}/exports` | Valid export request | 202 Accepted with export_id |
| IT-EP-050 | `GET /api/v1/orgs/{orgId}/exports/{id}` | Completed export | 200 with download_url |

##### Rate Limiting

| Test ID | Endpoint | Scenario | Expected |
|---|---|---|---|
| IT-EP-051 | Any endpoint | Rate limit exceeded (>100 req/min) | 429 with Retry-After header |

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

Frontend unit tests cover React components (rendering, interaction), context providers, utility functions, and API service modules. *(Not yet implemented — test cases below define the target coverage.)*

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
| FE-SC-001 | `DateRangeSelector` | Renders with default "Last 30 days" | Shows correct from/to dates in date inputs |
| FE-SC-002 | `DateRangeSelector` | Click "Last 7 days" preset button | Emits updated date range via callback |
| FE-SC-003 | `DateRangeSelector` | Click "Last 90 days" preset button | Emits 90-day date range |
| FE-SC-004 | `DateRangeSelector` | Change custom `from` date input | Emits updated range, `from` max-constrained to `to` value |
| FE-SC-005 | `DateRangeSelector` | Change custom `to` date input | Emits updated range, `to` max-constrained to today |
| FE-SC-006 | `MetricCard` | Render with number variant | Displays locale-formatted number (e.g., "142,857") |
| FE-SC-007 | `MetricCard` | Render with currency variant | Displays formatted currency (e.g., "$24,350.12") |
| FE-SC-008 | `MetricCard` | Render with percentage variant | Displays percentage (e.g., "94.5%") |
| FE-SC-009 | `MetricCard` | Render with duration variant | Displays duration in ms or seconds |
| FE-SC-010 | `StatusBadge` | Render with SUCCEEDED status | Green pill badge |
| FE-SC-011 | `StatusBadge` | Render with FAILED status | Red pill badge |
| FE-SC-012 | `StatusBadge` | Render with RUNNING status | Blue pill badge |
| FE-SC-013 | `StatusBadge` | Render with CANCELLED status | Slate/gray pill badge |
| FE-SC-014 | `Layout` | Renders sidebar with nav items for ORG_ADMIN | Shows "Organization" link + all team links + "My Dashboard" |
| FE-SC-015 | `Layout` | Renders sidebar for TEAM_LEAD | Shows user's team links + "My Dashboard" |
| FE-SC-016 | `Layout` | Renders sidebar for MEMBER | Shows only "My Dashboard" |
| FE-SC-017 | `Layout` | Mobile view (< 768px) | Sidebar collapsed, hamburger menu visible |
| FE-SC-018 | `Layout` | Click hamburger menu on mobile | Sidebar opens with overlay backdrop |
| FE-SC-019 | `Layout` | Route change on mobile | Sidebar auto-closes |
| FE-SC-020 | `Layout` | Sidebar footer | Displays user display name, role, logout button |
| FE-SC-021 | `Layout` | Click logout | Clears auth state, redirects to /login |

#### 5.5.2 Page Components

##### LoginPage

| Test ID | Page | Scenario | Expected |
|---|---|---|---|
| FE-PG-001 | `LoginPage` | Render login form | Shows email and password inputs, sign-in button |
| FE-PG-002 | `LoginPage` | Render test accounts | Shows 4 test accounts with quick-fill buttons (Acme ORG_ADMIN, TEAM_LEAD, MEMBER; Globex ORG_ADMIN) |
| FE-PG-003 | `LoginPage` | Click test account | Pre-fills email and password fields |
| FE-PG-004 | `LoginPage` | Submit valid credentials | Calls login API, stores token, redirects to `/` |
| FE-PG-005 | `LoginPage` | Submit invalid credentials | Shows error banner |
| FE-PG-006 | `LoginPage` | Already authenticated user | Redirects to `/` |

##### OrgDashboard

| Test ID | Page | Scenario | Expected |
|---|---|---|---|
| FE-PG-007 | `OrgDashboard` | Render with data | Displays org name, 5 metric cards (Total Runs, Success Rate, Failed Runs, Total Tokens, Total Cost), chart, team breakdown, agent type breakdown, top users |
| FE-PG-008 | `OrgDashboard` | Loading state | Shows "Loading organization analytics..." text |
| FE-PG-009 | `OrgDashboard` | Change date range | All data refetches with new from/to parameters |
| FE-PG-010 | `OrgDashboard` | Click data point on Daily Runs chart (total) | Navigates to `/org/runs?from={date}&to={date}` |
| FE-PG-011 | `OrgDashboard` | Click data point on succeeded line | Navigates to `/org/runs?from={date}&to={date}&status=SUCCEEDED` |
| FE-PG-012 | `OrgDashboard` | Click data point on failed line | Navigates to `/org/runs?from={date}&to={date}&status=FAILED` |
| FE-PG-013 | `OrgDashboard` | Click team row in breakdown table | Navigates to `/teams/:teamId` |
| FE-PG-014 | `OrgDashboard` | Click user row in top users | Navigates to `/users/:userId` |
| FE-PG-015 | `OrgDashboard` | Auto-refresh | Data refreshes every 15 seconds via setInterval |
| FE-PG-016 | `OrgDashboard` | Click refresh button | Data reloads, "Last updated" timestamp updates |
| FE-PG-017 | `OrgDashboard` | Last updated display | Shows locale time string after successful fetch |
| FE-PG-018 | `OrgDashboard` | Agent type pie chart | Uses external Legend component (no inline labels) |

##### RunsListPage

| Test ID | Page | Scenario | Expected |
|---|---|---|---|
| FE-PG-019 | `RunsListPage` | Render with paginated runs | Shows runs table with columns: Time, User, Team, Agent Type, Status, Duration, Tokens, Cost |
| FE-PG-020 | `RunsListPage` | Date range filter | Filters runs by from/to dates |
| FE-PG-021 | `RunsListPage` | Status multi-select filter | Shows checkbox dropdown with SUCCEEDED, FAILED, CANCELLED, RUNNING options |
| FE-PG-022 | `RunsListPage` | Team single-select filter | Populates dropdown from `/orgs/{orgId}/teams` API |
| FE-PG-023 | `RunsListPage` | User multi-select filter | Populates dropdown from `/orgs/{orgId}/users` API |
| FE-PG-024 | `RunsListPage` | Active filter pills | Shows removable pills for active filters |
| FE-PG-025 | `RunsListPage` | Clear all filters | Resets all filters to defaults |
| FE-PG-026 | `RunsListPage` | URL query param sync | All filter state persisted in URL via `useSearchParams` |
| FE-PG-027 | `RunsListPage` | Pagination controls | Shows page numbers with Previous/Next buttons, 25 items per page |
| FE-PG-028 | `RunsListPage` | Click run row | Navigates to `/runs/:runId` |
| FE-PG-029 | `RunsListPage` | Back to Dashboard link | Navigates back to `/org` |
| FE-PG-030 | `RunsListPage` | Total count display | Shows "N runs found" |

##### TeamDashboard

| Test ID | Page | Scenario | Expected |
|---|---|---|---|
| FE-PG-031 | `TeamDashboard` | Render for specific team | Shows "Team: {TeamName}" header, 5 metric cards, timeseries chart, by-user breakdown |
| FE-PG-032 | `TeamDashboard` | Team name resolution | Resolves name from user's teams; for ORG_ADMIN, fetches via teams API |
| FE-PG-033 | `TeamDashboard` | Change date range | All data refetches |
| FE-PG-034 | `TeamDashboard` | ORG_ADMIN clicks chart data point | Navigates to `/org/runs` with team_id and date filters |
| FE-PG-035 | `TeamDashboard` | Click user row in by-user table | Navigates to `/users/:userId` (ORG_ADMIN and TEAM_LEAD) |
| FE-PG-036 | `TeamDashboard` | Auto-refresh | Data refreshes every 15 seconds |
| FE-PG-037 | `TeamDashboard` | Click refresh button | Data reloads, timestamp updates |

##### PersonalDashboard

| Test ID | Page | Scenario | Expected |
|---|---|---|---|
| FE-PG-038 | `PersonalDashboard` | Render with user data | Shows 4 metric cards (My Runs, Success Rate, Total Tokens, Total Cost), rank callout, chart, recent runs |
| FE-PG-039 | `PersonalDashboard` | Team rank display | Shows "You are #N of M engineers in your organization this period" |
| FE-PG-040 | `PersonalDashboard` | Recent runs table | Shows up to 20 most recent runs |
| FE-PG-041 | `PersonalDashboard` | Click run row | Navigates to `/runs/:runId` |
| FE-PG-042 | `PersonalDashboard` | Change date range | All data refetches |
| FE-PG-043 | `PersonalDashboard` | Auto-refresh | Data refreshes every 15 seconds |
| FE-PG-044 | `PersonalDashboard` | Click refresh button | Data reloads, timestamp updates |

##### UserDashboard

| Test ID | Page | Scenario | Expected |
|---|---|---|---|
| FE-PG-045 | `UserDashboard` | ORG_ADMIN viewing another user | Shows "{DisplayName} — Analytics" header, metric cards, rank, chart, recent runs |
| FE-PG-046 | `UserDashboard` | TEAM_LEAD viewing shared-team member | Shows user analytics |
| FE-PG-047 | `UserDashboard` | 403 Forbidden response | Shows permission error message |
| FE-PG-048 | `UserDashboard` | 404 Not Found response | Shows user not found message |
| FE-PG-049 | `UserDashboard` | Click back button | Navigates to previous page |
| FE-PG-050 | `UserDashboard` | Click run row | Navigates to `/runs/:runId` |

##### RunDetail

| Test ID | Page | Scenario | Expected |
|---|---|---|---|
| FE-PG-051 | `RunDetail` | Render succeeded run | Shows run metadata (ID, agent type, model, status, timestamps, duration), token breakdown, cost breakdown; no error section |
| FE-PG-052 | `RunDetail` | Render failed run | Shows error category and message in error details section |
| FE-PG-053 | `RunDetail` | Click back button | Navigates to previous page via `navigate(-1)` |
| FE-PG-054 | `RunDetail` | Status badge display | Shows color-coded StatusBadge component |

#### 5.5.3 Routing & Authorization

| Test ID | Scenario | Expected |
|---|---|---|
| FE-RT-001 | ORG_ADMIN navigates to `/` | Redirected to `/org` |
| FE-RT-002 | TEAM_LEAD navigates to `/` | Redirected to `/teams/:firstTeamId` |
| FE-RT-003 | MEMBER navigates to `/` | Redirected to `/me` |
| FE-RT-004 | Unauthenticated user visits any protected route | Redirected to `/login` |
| FE-RT-005 | Authenticated user visits `/login` | Redirected to `/` |
| FE-RT-006 | All roles can access `/runs/:runId` | Run detail accessible (backend enforces authorization) |
| FE-RT-007 | All roles can access `/me` | Personal dashboard accessible |
| FE-RT-008 | ORG_ADMIN can access `/org/runs` | Runs list page accessible |
| FE-RT-009 | ORG_ADMIN / TEAM_LEAD can access `/users/:userId` | User dashboard accessible |
| FE-RT-010 | 401 API response on any page | Clears localStorage, redirects to `/login` (except on login page itself) |

#### 5.5.4 Auth Context

| Test ID | Scenario | Expected |
|---|---|---|
| FE-AC-001 | `login()` with valid credentials | Stores token and user in localStorage, sets isAuthenticated=true |
| FE-AC-002 | `login()` with invalid credentials | Throws error, isAuthenticated remains false |
| FE-AC-003 | `logout()` called | Clears localStorage, sets user to null, isAuthenticated=false |
| FE-AC-004 | Page reload with stored token | Restores user from localStorage |
| FE-AC-005 | Axios interceptor | Attaches `Authorization: Bearer {token}` to all requests |
| FE-AC-006 | 401 response interceptor | Clears auth, redirects to login (skips redirect for login requests) |

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

Each E2E test suite uses a dedicated test data seed that is loaded before the suite runs and cleaned up after. The current H2 `DataSeeder` provides rich test data including:

- **2 organizations:** Acme Corp and Globex Industries
- **10 teams** across both orgs (Platform, Data Science, Security, DevOps, etc.)
- **48 users** with varied roles (ORG_ADMIN, TEAM_LEAD, MEMBER)
- **90 days of intensive agent run data** with realistic distributions

Seeds are defined as SQL fixtures for production environments:

```
e2e/
├── fixtures/
│   ├── seed-org-dashboard.sql       # Org with teams, users, agent runs
│   ├── seed-budget-management.sql   # Org with budgets at various thresholds
│   ├── seed-multi-org.sql           # Multiple orgs for cross-org isolation tests
│   └── seed-empty-org.sql           # Org with no agent runs
```

**Test accounts (password: `password123`):**

| Email | Role | Organization |
|---|---|---|
| `admin@acme.com` | ORG_ADMIN | Acme Corp |
| `lead-platform@acme.com` | TEAM_LEAD | Acme Corp |
| `member1@acme.com` | MEMBER | Acme Corp |
| `admin2@globex.com` | ORG_ADMIN | Globex Industries |

### 7.5 Acceptance Test Suites

#### 7.5.1 Suite: Login & Authentication

| Test ID | Scenario | Steps | Expected Result |
|---|---|---|---|
| AT-LG-001 | Login with valid credentials | 1. Navigate to /login 2. Enter email and password 3. Click "Sign In" | Redirected to role-appropriate dashboard |
| AT-LG-002 | Login with invalid credentials | 1. Navigate to /login 2. Enter wrong password 3. Click "Sign In" | Error banner displayed, stays on login page |
| AT-LG-003 | Quick-fill test account | 1. Navigate to /login 2. Click test account button | Email and password fields pre-filled |
| AT-LG-004 | ORG_ADMIN default redirect | 1. Login as ORG_ADMIN | Redirected to `/org` |
| AT-LG-005 | TEAM_LEAD default redirect | 1. Login as TEAM_LEAD | Redirected to `/teams/:firstTeamId` |
| AT-LG-006 | MEMBER default redirect | 1. Login as MEMBER | Redirected to `/me` |
| AT-LG-007 | Logout flow | 1. Login 2. Click "Logout" in sidebar | Redirected to /login, session cleared |
| AT-LG-008 | Multi-org isolation | 1. Login as Acme admin 2. Verify Acme data 3. Logout 4. Login as Globex admin | Each org sees only their own data |

#### 7.5.2 Suite: Organization Dashboard (Requirements ORG-1 through ORG-10)

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
| AT-ORG-017 | ORG-10 | Export as CSV *(planned)* | 1. Click "Export" button 2. Wait for completion | CSV file downloads with correct data |
| AT-ORG-018 | — | Click Daily Runs chart (total) | 1. Click data point on total runs line | Navigates to `/org/runs?from={date}&to={date}` |
| AT-ORG-019 | — | Click Daily Runs chart (succeeded) | 1. Click data point on succeeded line | Navigates to `/org/runs?from={date}&to={date}&status=SUCCEEDED` |
| AT-ORG-020 | — | Click Daily Runs chart (failed) | 1. Click data point on failed line | Navigates to `/org/runs?from={date}&to={date}&status=FAILED` |
| AT-ORG-021 | — | Click team row in breakdown | 1. Click a team row | Navigates to `/teams/:teamId` |
| AT-ORG-022 | — | Click user in top users | 1. Click a user row in Top Users | Navigates to `/users/:userId` |
| AT-ORG-023 | — | Auto-refresh | 1. Navigate to /org 2. Wait 15 seconds | Data refreshes automatically, timestamp updates |
| AT-ORG-024 | — | Manual refresh | 1. Click refresh button | Data reloads, "Last updated" timestamp updates |
| AT-ORG-025 | — | Agent type pie chart legend | 1. View agent type breakdown | Pie chart uses external legend (no inline label overflow) |

#### 7.5.3 Suite: Organization Runs List

| Test ID | Requirement | Scenario | Steps | Expected Result |
|---|---|---|---|---|
| AT-RL-001 | — | View paginated runs | 1. Login as ORG_ADMIN 2. Navigate to /org/runs | Runs table displayed with 25 items per page, total count shown |
| AT-RL-002 | — | Filter by date range | 1. Change from/to dates | Runs filtered to date range, URL params updated |
| AT-RL-003 | — | Filter by status (multi-select) | 1. Open status dropdown 2. Check FAILED and CANCELLED | Only FAILED and CANCELLED runs shown |
| AT-RL-004 | — | Filter by team | 1. Select team from dropdown | Runs filtered to selected team |
| AT-RL-005 | — | Filter by user (multi-select) | 1. Open user dropdown 2. Select multiple users | Runs filtered to selected users |
| AT-RL-006 | — | Combine multiple filters | 1. Apply date + status + team filters | All filters applied simultaneously |
| AT-RL-007 | — | Active filter pills | 1. Apply filters | Filter pills shown below controls with remove (×) buttons |
| AT-RL-008 | — | Clear all filters | 1. Apply filters 2. Click "Clear all filters" | All filters reset to defaults |
| AT-RL-009 | — | URL state persistence | 1. Apply filters 2. Copy URL 3. Open in new tab | Same view with same filters restored from URL params |
| AT-RL-010 | — | Pagination navigation | 1. Click page 2 button | Page 2 of results displayed |
| AT-RL-011 | — | Click run row | 1. Click a run in the table | Navigates to `/runs/:runId` |
| AT-RL-012 | — | Navigate from chart click | 1. On org dashboard, click a failed data point | Arrives at runs list with date and FAILED status pre-filtered |
| AT-RL-013 | — | Back to Dashboard | 1. Click "Back to Dashboard" link | Navigates to `/org` |

#### 7.5.4 Suite: Team Dashboard (Requirements TEAM-1 through TEAM-5)

| Test ID | Requirement | Scenario | Steps | Expected Result |
|---|---|---|---|---|
| AT-TM-001 | TEAM-1 | View team KPIs | 1. Login as TEAM_LEAD 2. Navigate to /teams/:teamId | Metric cards show team-scoped runs, tokens, cost |
| AT-TM-002 | TEAM-2 | View user breakdown | 1. Navigate to team dashboard 2. Scroll to "Usage by User" | Table shows per-user metrics |
| AT-TM-003 | TEAM-3 | View agent type breakdown | 1. Scroll to "Usage by Agent Type" | Table shows per-agent-type metrics |
| AT-TM-004 | TEAM-4 | Change date range | 1. Select "Last 90 days" | All data refreshes |
| AT-TM-005 | TEAM-4 | Apply agent type filter | 1. Select agent type in filter | Data filtered |
| AT-TM-006 | TEAM-5 | Switch between teams | 1. Click team in sidebar nav | Dashboard reloads with new team data |
| AT-TM-007 | — | ORG_ADMIN views any team | 1. Login as ORG_ADMIN 2. Navigate to any team via sidebar | Team dashboard accessible, team name resolved via API |
| AT-TM-008 | — | Click user row | 1. Click user in by-user table | Navigates to `/users/:userId` (ORG_ADMIN and TEAM_LEAD) |
| AT-TM-009 | — | ORG_ADMIN clicks chart point | 1. Click data point in timeseries | Navigates to `/org/runs` with team_id and date filters |
| AT-TM-010 | — | Auto-refresh | 1. Stay on team dashboard 2. Wait 15 seconds | Data refreshes automatically |
| AT-TM-011 | — | Manual refresh | 1. Click refresh button | Data reloads, timestamp updates |

#### 7.5.5 Suite: Personal Dashboard (Requirements USER-1 through USER-6)

| Test ID | Requirement | Scenario | Steps | Expected Result |
|---|---|---|---|---|
| AT-US-001 | USER-1 | View personal KPIs | 1. Login as MEMBER 2. Navigate to /me | Metric cards show user's runs, tokens, cost |
| AT-US-002 | USER-2 | View personal time-series | 1. Navigate to /me 2. Observe chart | Line chart shows user's daily usage |
| AT-US-003 | USER-3 | View recent runs table | 1. Scroll to "Recent Runs" | Table shows runs with all specified columns |
| AT-US-004 | USER-4 | View run details | 1. Click a run row | Navigate to /runs/:runId, shows full metadata |
| AT-US-005 | USER-4 | View failed run details | 1. Click a failed run | Error category and message displayed |
| AT-US-006 | USER-5 | Change date range | 1. Select "Last 7 days" | Personal data refreshes |
| AT-US-007 | USER-6 | View team rank | 1. Navigate to /me | Rank callout shows "You are #N of M engineers in your organization this period" |
| AT-US-008 | — | Auto-refresh | 1. Stay on personal dashboard 2. Wait 15 seconds | Data refreshes automatically |
| AT-US-009 | — | Manual refresh | 1. Click refresh button | Data reloads, timestamp updates |
| AT-US-010 | — | Recent runs limited | 1. User with >20 runs | Table shows max 20 most recent runs |

#### 7.5.6 Suite: User Dashboard (Cross-Role Access)

| Test ID | Requirement | Scenario | Steps | Expected Result |
|---|---|---|---|---|
| AT-UD-001 | — | ORG_ADMIN views any user | 1. Login as ORG_ADMIN 2. Navigate to /users/:userId | User analytics displayed with name, metrics, rank, chart, runs |
| AT-UD-002 | — | TEAM_LEAD views team member | 1. Login as TEAM_LEAD 2. Click team member in by-user table | User dashboard shows team member's analytics |
| AT-UD-003 | — | TEAM_LEAD views non-team user | 1. Login as TEAM_LEAD 2. Navigate to /users/:userId (different team) | Permission error message displayed |
| AT-UD-004 | — | MEMBER tries to access user dashboard | 1. Login as MEMBER 2. Navigate to /users/:userId | Permission error or redirect |
| AT-UD-005 | — | User not found | 1. Navigate to /users/non-existent-id | Not found error message displayed |
| AT-UD-006 | — | Click run row | 1. Click run in recent runs table | Navigates to `/runs/:runId` |
| AT-UD-007 | — | Back navigation | 1. Click "Back" button | Returns to previous page |
| AT-UD-008 | — | Navigate from org top users | 1. On org dashboard, click user in top users table | Arrives at user dashboard for that user |

#### 7.5.7 Suite: Run Detail (Requirements RUN-1 through RUN-6)

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

#### 7.5.8 Suite: Budget Management (Requirements ALERT-1 through ALERT-5)

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

#### 7.5.9 Suite: Data Freshness (Requirements FRESH-1 through FRESH-4)

| Test ID | Requirement | Scenario | Steps | Expected Result |
|---|---|---|---|---|
| AT-FR-001 | FRESH-1 | Data within 5 min | 1. Ingest new event 2. Wait 3. Refresh dashboard | New data appears within 5 minutes |
| AT-FR-002 | FRESH-2 | Last updated indicator | 1. Navigate to any dashboard | "Last updated: {locale time}" shown |
| AT-FR-003 | FRESH-3 | Manual refresh | 1. Click refresh button | Data reloads, timestamp updates |
| AT-FR-004 | FRESH-4 | Running agent indicator | 1. Seed a RUNNING agent run 2. Navigate to /me | Running indicator shown on that run (RUNNING status badge) |
| AT-FR-005 | — | Auto-refresh interval | 1. Navigate to org dashboard 2. Observe | Data refreshes every 15 seconds via setInterval |
| AT-FR-006 | — | Auto-refresh on team dashboard | 1. Navigate to team dashboard 2. Wait | Auto-refreshes at 15-second intervals |
| AT-FR-007 | — | Auto-refresh on personal dashboard | 1. Navigate to /me 2. Wait | Auto-refreshes at 15-second intervals |

#### 7.5.10 Suite: Authentication & Authorization (Requirements AUTH-1 through AUTH-6)

| Test ID | Requirement | Scenario | Steps | Expected Result |
|---|---|---|---|---|
| AT-AU-001 | AUTH-1 | Login via email/password | 1. Navigate to /login 2. Enter credentials 3. Submit | JWT token stored, redirected to dashboard |
| AT-AU-002 | AUTH-2 | Role-based navigation | 1. Login as each role | Default redirect matches role (ORG_ADMIN→/org, TEAM_LEAD→/teams/:id, MEMBER→/me) |
| AT-AU-003 | AUTH-3 | ORG_ADMIN full access | 1. Login as ORG_ADMIN 2. Visit org, team, user, and run pages | All pages accessible, sidebar shows all teams |
| AT-AU-004 | AUTH-4 | TEAM_LEAD scoped access | 1. Login as TEAM_LEAD 2. Visit own team 3. Try other team | Own team accessible; sidebar shows only assigned teams |
| AT-AU-005 | AUTH-5 | MEMBER restricted access | 1. Login as MEMBER 2. Navigate to /me | Personal dashboard accessible, sidebar shows only "My Dashboard" |
| AT-AU-006 | AUTH-5 | Cross-role team lead access | 1. Login as TEAM_LEAD 2. View team member's user dashboard | User dashboard accessible for shared team members |
| AT-AU-007 | AUTH-6 | JWT roles mapped to sidebar | 1. Login 2. Verify sidebar nav items match role | Navigation items match JWT role claims |
| AT-AU-008 | — | Multi-org data isolation | 1. Login as Acme admin 2. Verify only Acme data visible | No Globex org data leaked |
| AT-AU-009 | — | 401 response handling | 1. Expire JWT 2. Interact with dashboard | Redirected to /login, no infinite redirect loop |

#### 7.5.11 Suite: Cross-Cutting Concerns

| Test ID | Scenario | Steps | Expected Result |
|---|---|---|---|
| AT-CC-001 | URL state persistence | 1. Apply filters + date range on /org/runs 2. Copy URL 3. Open in new tab | Same view with same filters restored from URL params |
| AT-CC-002 | Browser back/forward | 1. Navigate between pages 2. Press back | Previous page restored with state |
| AT-CC-003 | Error recovery | 1. Simulate API error (backend unavailable) | Error logged, Vite proxy errors handled gracefully |
| AT-CC-004 | Responsive - desktop (>=1024px) | 1. View at 1024px+ width | Full sidebar visible, multi-column metric grids, two-column chart sections |
| AT-CC-005 | Responsive - tablet (768-1023px) | 1. Resize to 768px | Sidebar visible, 3-column metric grid, charts full-width |
| AT-CC-006 | Responsive - mobile (<768px) | 1. Resize to 375px | Sidebar collapsed, fixed top header with hamburger, 2-column metric grid |
| AT-CC-007 | Mobile hamburger menu | 1. On mobile, tap hamburger icon | Sidebar opens with semi-transparent overlay |
| AT-CC-008 | Mobile sidebar auto-close | 1. On mobile, open sidebar 2. Navigate to a page | Sidebar closes automatically on route change |
| AT-CC-009 | Keyboard navigation | 1. Tab through all interactive elements | All elements reachable, ARIA labels present (e.g., menu toggle) |
| AT-CC-010 | Session expiry | 1. JWT expires 2. Interact with dashboard | 401 intercepted, redirected to /login (no redirect loop) |
| AT-CC-011 | Vite proxy error handling | 1. Access frontend when backend is down | Proxy errors handled, no crash |

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

### 11.2 H2 DataSeeder (Current Development)

The `DataSeeder` class (`@Component`, runs on `ApplicationReadyEvent`) seeds the H2 in-memory database with rich test data:

| Entity | Count | Details |
|---|---|---|
| Organizations | 2 | Acme Corp, Globex Industries |
| Teams | 10 | 6 Acme teams (Platform, Data Science, Security, DevOps, Frontend, ML Ops), 4 Globex teams |
| Users | 48 | Mix of ORG_ADMIN, TEAM_LEAD, MEMBER across both orgs |
| Agent Types | 6 | code_review, test_generation, code_completion, bug_triage, doc_generation, security_scan |
| Agent Runs | ~90 days | Intensive daily run data with realistic distributions (70% succeeded, 20% failed, 5% cancelled, 5% running) |
| Budgets | Per org | Organization-scope budgets with standard thresholds |

### 11.3 Test Data Builders (Backend — planned)

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

### 11.4 Test Factories (Frontend — planned)

```typescript
// src/test/factories.ts
import { faker } from '@faker-js/faker';

export function buildOrgSummary(overrides?: Partial<OrgSummary>): OrgSummary {
  return {
    orgId: faker.string.uuid(),
    period: { from: '2026-01-01', to: '2026-01-31' },
    totalRuns: 142857,
    succeededRuns: 135000,
    failedRuns: 7500,
    cancelledRuns: 357,
    runningRuns: 0,
    successRate: 0.9451,
    totalTokens: 58000000000,
    totalInputTokens: 42000000000,
    totalOutputTokens: 16000000000,
    totalCost: '24350.120000',
    avgDurationMs: 34500,
    p50DurationMs: 28000,
    p95DurationMs: 95000,
    p99DurationMs: 180000,
    ...overrides,
  };
}

export function buildAgentRun(overrides?: Partial<AgentRun>): AgentRun {
  return {
    runId: faker.string.uuid(),
    agentType: 'code_review',
    agentTypeDisplayName: 'Code Review Agent',
    status: 'SUCCEEDED',
    startedAt: '2026-01-15T08:30:00Z',
    finishedAt: '2026-01-15T08:30:34Z',
    durationMs: 34000,
    totalTokens: 480000,
    totalCost: '0.198000',
    ...overrides,
  };
}

export function buildBudget(overrides?: Partial<Budget>): Budget {
  return {
    id: faker.string.uuid(),
    orgId: faker.string.uuid(),
    scope: 'ORGANIZATION',
    scopeId: faker.string.uuid(),
    monthlyLimit: 50000.000000,
    thresholds: '0.50,0.80,1.00',
    notificationChannels: 'IN_APP,EMAIL',
    ...overrides,
  };
}
```

---

## 12. Traceability Matrix

Maps every product requirement to its test coverage across unit, integration, and E2E layers.

| Requirement ID | Description | Unit Tests | Integration Tests | E2E Tests |
|---|---|---|---|---|
| AUTH-1 | Email/password authentication | UT-AU-001..011, UT-CT-001..007 | IT-EP-001..005 | AT-LG-001..003, AT-AU-001 |
| AUTH-2 | Three roles (ORG_ADMIN, TEAM_LEAD, MEMBER) | UT-AU-016..023 | IT-EP-006..007 | AT-LG-004..006, AT-AU-002 |
| AUTH-3 | ORG_ADMIN views all org data | UT-AU-016, UT-AU-020 | IT-EP-006 | AT-AU-003 |
| AUTH-4 | TEAM_LEAD views own teams | UT-AU-018, UT-AU-021..022 | IT-EP-021..022 | AT-AU-004, AT-TM-007 |
| AUTH-5 | MEMBER views own data | UT-AU-017, UT-AU-023 | IT-EP-026 | AT-AU-005 |
| AUTH-6 | Roles from JWT claims | UT-AU-001..011, UT-AU-024..030 | IT-EP-004..005 | AT-AU-007 |
| ORG-1 | Aggregate KPIs | UT-AN-001..009 | IT-EP-006, IT-EP-009 | AT-ORG-001 |
| ORG-2 | Time-series chart | UT-AN-016..020 | IT-EP-010 | AT-ORG-002 |
| ORG-3 | Team breakdown | UT-AN-023..026 | IT-EP-011 | AT-ORG-003, AT-ORG-021 |
| ORG-4 | Agent type breakdown | UT-AN-027..029 | IT-EP-012..013 | AT-ORG-004, AT-ORG-025 |
| ORG-5 | Top users | UT-AN-030..034 | IT-EP-014..015 | AT-ORG-005..006, AT-ORG-022 |
| ORG-6 | Success rate trend | UT-AN-003 | IT-EP-010 | AT-ORG-007 |
| ORG-7 | Avg duration trend | UT-AN-004 | IT-EP-010 | AT-ORG-008 |
| ORG-8 | Date range controls | FE-SC-001..005 | — | AT-ORG-009..011 |
| ORG-9 | Filters | UT-AN-005, UT-CT-009, UT-CT-018 | IT-EP-009 | AT-ORG-012..016 |
| ORG-10 | CSV export *(planned)* | UT-EX-001..008 | IT-EP-049..050 | AT-ORG-017 |
| — | Clickable chart navigation | FE-PG-010..012 | — | AT-ORG-018..020, AT-RL-012 |
| — | Org runs list with filters | UT-AN-046..052, UT-CT-017..020 | IT-EP-016..020 | AT-RL-001..013 |
| — | Cross-role user dashboard | UT-CT-048..064 | IT-EP-030..036 | AT-UD-001..008 |
| TEAM-1 | Team KPIs | UT-AN-010..012 | IT-EP-021..023 | AT-TM-001 |
| TEAM-2 | User breakdown | UT-AN-035..036 | IT-EP-025 | AT-TM-002, AT-TM-008 |
| TEAM-3 | Agent type breakdown | UT-AN-027..029 | IT-EP-012 | AT-TM-003 |
| TEAM-4 | Date range + filters | UT-AN-012, UT-CT-032 | IT-EP-021 | AT-TM-004..005 |
| TEAM-5 | Team switching | — | — | AT-TM-006..007 |
| USER-1 | Personal KPIs | UT-AN-013..015 | IT-EP-026 | AT-US-001 |
| USER-2 | Personal time-series | UT-AN-022 | IT-EP-027 | AT-US-002 |
| USER-3 | Recent runs table | UT-AN-037..040 | IT-EP-028..029 | AT-US-003, AT-US-010 |
| USER-4 | Run detail drill-down | UT-AN-041..045, UT-CT-043..047 | IT-EP-037..041 | AT-US-004..005 |
| USER-5 | Date range controls | FE-SC-001..005 | — | AT-US-006 |
| USER-6 | Team rank | UT-AN-014 | IT-EP-026 | AT-US-007 |
| RUN-1 | Run metadata | UT-AN-041 | IT-EP-037 | AT-RN-001 |
| RUN-2 | Token breakdown | UT-AN-041, UT-AN-009 | IT-EP-037 | AT-RN-002 |
| RUN-3 | Cost breakdown | UT-AN-041, UT-AN-006 | IT-EP-037 | AT-RN-003 |
| RUN-4 | Model info | UT-AN-041 | IT-EP-037 | AT-RN-004 |
| RUN-5 | Error display for failed runs | UT-AN-045 | IT-EP-037 | AT-RN-005..006 |
| RUN-6 | Access control on run detail | UT-CT-043..047 | IT-EP-037..041 | AT-RN-007..009 |
| ALERT-1 | Org budget creation *(planned)* | UT-BG-002 | IT-EP-047 | AT-BG-001 |
| ALERT-2 | Team budget creation *(planned)* | UT-BG-003 | IT-EP-047 | AT-BG-002 |
| ALERT-3 | Threshold notifications *(planned)* | UT-BG-010..013 | — | AT-BG-003..005 |
| ALERT-4 | Custom thresholds *(planned)* | UT-BG-014 | — | AT-BG-006 |
| ALERT-5 | Budget gauge display *(planned)* | — | — | AT-BG-007..008 |
| FRESH-1 | Data < 5 min stale | — | IT-CA-001..003 | AT-FR-001 |
| FRESH-2 | Last updated indicator | FE-PG-017 | — | AT-FR-002 |
| FRESH-3 | Manual refresh | FE-PG-016 | IT-CA-004 | AT-FR-003 |
| FRESH-4 | Running indicator | FE-SC-012 | — | AT-FR-004 |
| — | Auto-refresh (15s interval) | FE-PG-015, FE-PG-036, FE-PG-043 | — | AT-FR-005..007, AT-ORG-023 |
| — | Mobile responsive sidebar | FE-SC-017..019 | — | AT-CC-006..008 |
| — | Login flow with test accounts | FE-PG-001..006, FE-AC-001..006 | IT-EP-001..003 | AT-LG-001..008 |
| — | Multi-org data isolation | UT-CT-029 | IT-EP-008, IT-EP-020 | AT-LG-008, AT-AU-008 |

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
