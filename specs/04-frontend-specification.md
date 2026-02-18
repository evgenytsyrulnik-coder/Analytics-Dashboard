# Frontend Specification

## 1. Technology Stack

| Layer | Technology |
|---|---|
| Framework | React 18 with TypeScript |
| Build Tool | Vite |
| Routing | React Router v6 |
| State Management | React `useState` / `useEffect` with Axios for server state |
| Charting | Recharts |
| Styling | Tailwind CSS (utility classes) |
| Date Handling | Native `Date` API |
| HTTP Client | Axios |
| Testing | Vitest + React Testing Library |

## 2. Page Structure & Routes

```
/login                          → Login page (unauthenticated)
/                               → Redirect based on role
/org                            → Organization Dashboard (ORG_ADMIN)
/org/runs                       → Organization Runs list with filters (ORG_ADMIN)
/teams/:teamId                  → Team Dashboard (ORG_ADMIN, TEAM_LEAD)
/me                             → Personal Dashboard (all roles)
/users/:userId                  → User Dashboard (ORG_ADMIN, TEAM_LEAD)
/runs/:runId                    → Run Detail (authorized users)
```

### Role-based redirect logic (`/`)

| Role | Redirects to |
|---|---|
| `ORG_ADMIN` | `/org` |
| `TEAM_LEAD` | `/teams/:firstTeamId` |
| `MEMBER` | `/me` |

All routes except `/login` are wrapped in a `PrivateRoute` guard that redirects unauthenticated users to `/login`.

## 3. Layout & Navigation

### 3.1 Sidebar Navigation

A persistent sidebar (`Layout` component) wraps all authenticated routes using React Router's `<Outlet />` pattern.

**Sidebar contents (role-dependent):**

| Role | Nav items shown |
|---|---|
| `ORG_ADMIN` | "Organization", all team links (fetched from API), "My Dashboard" |
| `TEAM_LEAD` | User's team links, "My Dashboard" |
| `MEMBER` | "My Dashboard" |

**Sidebar footer:** Displays the logged-in user's display name, role, and a Logout button.

### 3.2 Mobile Responsiveness

- On screens < 768px (`md` breakpoint), the sidebar collapses off-screen.
- A fixed top header bar appears with a hamburger toggle button and app title.
- Tapping the hamburger opens the sidebar with a semi-transparent overlay backdrop.
- The sidebar automatically closes on route change.

## 4. Shared Components

### 4.1 `DateRangeSelector`

- Preset buttons: "Last 7 days", "Last 30 days", "Last 90 days".
- Custom range picker with two native `<input type="date">` fields.
- `from` input max-constrained to `to` value; `to` input max-constrained to today.
- Emits `(from: string, to: string)` callback with ISO date strings.

### 4.2 `MetricCard`

- Displays a single KPI: label and formatted value.
- Variants: `number` (default, locale-formatted), `currency` (formatted as `$24,350.12`), `percentage` (multiplied by 100, e.g. `94.5%`), `duration` (ms or seconds).
- Rendered as a white card with border and shadow.

### 4.3 `StatusBadge`

- Displays a run status as a color-coded rounded pill.
- Color mapping:

| Status | Style |
|---|---|
| `SUCCEEDED` | Green background, green text |
| `FAILED` | Red background, red text |
| `RUNNING` | Blue background, blue text |
| `CANCELLED` | Slate/gray background, slate text |

## 5. Page Layouts

### 5.1 Login Page (`/login`)

```
┌──────────────────────────────────────────────────────────────────┐
│                    Agent Analytics Dashboard                     │
│                    Sign in to your account                       │
├──────────────────────────────────────────────────────────────────┤
│  [Error banner if login fails]                                   │
│                                                                  │
│  Email:    [________________________]                            │
│  Password: [________________________]                            │
│                      [Sign In]                                   │
├──────────────────────────────────────────────────────────────────┤
│  Test Accounts (password: password123)                           │
│  ┌──────────────────────────────────────────────────────────────┐│
│  │ Alice Chen    admin@acme.com               [ORG_ADMIN]      ││
│  │ Raj Patel     lead-platform@acme.com       [TEAM_LEAD]      ││
│  │ Liam Brown    member1@acme.com             [MEMBER]         ││
│  │ Edward Kim    admin2@globex.com            [ORG_ADMIN]      ││
│  └──────────────────────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────────────────┘
```

- Clicking a test account pre-fills the email and password fields.
- Already-authenticated users are redirected to `/`.

### 5.2 Organization Dashboard (`/org`)

```
┌──────────────────────────────────────────────────────────────────┐
│  Header: "{OrgName} Analytics"    [DateRangeSelector] [Refresh]  │
├──────────────────────────────────────────────────────────────────┤
│  Last updated: 2:30:15 PM                                        │
├──────────────────────────────────────────────────────────────────┤
│  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌───────────┐ │
│  │Total    │ │Success  │ │ Failed  │ │ Total   │ │ Total     │ │
│  │Runs     │ │Rate     │ │ Runs    │ │ Tokens  │ │ Cost      │ │
│  │142,857  │ │94.5%    │ │ 7,500   │ │ 58.0B   │ │ $24,350   │ │
│  └─────────┘ └─────────┘ └─────────┘ └─────────┘ └───────────┘ │
├──────────────────────────────────────────────────────────────────┤
│  Daily Runs (clickable — data points navigate to /org/runs)      │
│  "Click on a data point to view the list of runs"                │
│  ┌──────────────────────────────────────────────────────────────┐│
│  │  Line Chart: Total Runs (blue), Succeeded (green),          ││
│  │              Failed (red)                                    ││
│  │  Click dot → /org/runs?from={date}&to={date}&status={line}  ││
│  └──────────────────────────────────────────────────────────────┘│
├─────────────────────────────┬────────────────────────────────────┤
│  Usage by Team              │  Usage by Agent Type               │
│  ┌───────────────────────┐  │  ┌────────────────────────────────┐│
│  │ Bar Chart             │  │  │ Pie Chart + external Legend    ││
│  │ + Table (clickable →  │  │  │ + Table                        ││
│  │   /teams/:teamId)     │  │  └────────────────────────────────┘│
│  └───────────────────────┘  │                                    │
├─────────────────────────────┴────────────────────────────────────┤
│  Top Users by Cost                                               │
│  ┌──────────────────────────────────────────────────────────────┐│
│  │ Table: Rank, Name (clickable → /users/:userId),             ││
│  │        Team, Runs, Tokens, Cost                              ││
│  └──────────────────────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────────────────┘
```

**Interactive behaviors:**
- Timeseries chart data points are clickable. Each series line (Total, Succeeded, Failed) navigates to `/org/runs` with date and status query params pre-filled.
- Team table rows navigate to `/teams/:teamId`.
- Top Users table rows navigate to `/users/:userId`.
- Auto-refresh every 15 seconds.

### 5.3 Organization Runs List (`/org/runs`)

```
┌──────────────────────────────────────────────────────────────────┐
│  ← Back to Dashboard                                             │
│  Runs                                                            │
├──────────────────────────────────────────────────────────────────┤
│  Filters                                      [Clear all filters]│
│  ┌──────────────────────────────────────────────────────────────┐│
│  │ From [____] to [____]  Team [▾]  Result [▾]  Person [▾]     ││
│  │ [status pill ×] [team pill ×] [person pill ×]               ││
│  └──────────────────────────────────────────────────────────────┘│
├──────────────────────────────────────────────────────────────────┤
│  1,234 runs found                                    [Updating…] │
│  ┌──────────────────────────────────────────────────────────────┐│
│  │ Time | User | Team | Agent Type | Status | Duration |       ││
│  │      |      |      |            |        | Tokens | Cost    ││
│  │ Click row → /runs/:runId                                    ││
│  └──────────────────────────────────────────────────────────────┘│
│  [Previous]  [1] [2] [3] ... [10]  [Next]                        │
└──────────────────────────────────────────────────────────────────┘
```

**Filter controls:**
- **Date range:** Two native date inputs with `from`/`to` constraints.
- **Team:** Single-select `<select>` dropdown populated from `/orgs/{orgId}/teams`.
- **Result (Status):** Multi-select checkbox dropdown with options: SUCCEEDED, FAILED, CANCELLED, RUNNING. Shows status badges.
- **Person (User):** Multi-select checkbox dropdown populated from `/orgs/{orgId}/users`, with scrollable list.
- **Active filter pills:** Displayed below filters with individual remove (×) buttons.
- **Clear all filters:** Resets all filters to defaults.

**Pagination:**
- Server-side pagination, 25 items per page.
- Compact page number buttons with ellipsis for large page counts.
- Previous/Next buttons with disabled state at boundaries.

**URL sync:** All filter state (from, to, status, team_id, user_id, page) is persisted in URL query params via `useSearchParams`.

### 5.4 Team Dashboard (`/teams/:teamId`)

```
┌──────────────────────────────────────────────────────────────────┐
│  Header: "Team: {TeamName}"              [DateRange] [Refresh]   │
├──────────────────────────────────────────────────────────────────┤
│  Last updated: 2:30:15 PM                                        │
├──────────────────────────────────────────────────────────────────┤
│  MetricCards: Total Runs, Success Rate, Failed Runs,             │
│               Total Tokens, Total Cost                           │
├──────────────────────────────────────────────────────────────────┤
│  Daily Runs                                                      │
│  ┌──────────────────────────────────────────────────────────────┐│
│  │  Line Chart: Total Runs, Succeeded, Failed                  ││
│  │  (ORG_ADMIN only: clickable dots → /org/runs with filters)  ││
│  └──────────────────────────────────────────────────────────────┘│
├──────────────────────────────────────────────────────────────────┤
│  Usage by User                                                   │
│  ┌──────────────────────────────────────────────────────────────┐│
│  │ Table: User, Runs, Tokens, Cost, Success Rate, Avg Duration ││
│  │ (ORG_ADMIN / TEAM_LEAD: click row → /users/:userId)         ││
│  └──────────────────────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────────────────┘
```

**Notes:**
- Team name is resolved from the user's team list. For ORG_ADMIN viewing teams they don't belong to, the name is fetched via the teams API.
- Chart data points are clickable only for ORG_ADMIN, navigating to `/org/runs?from={date}&to={date}&team_id={teamId}&status={status}`.
- Usage by User table rows are clickable for ORG_ADMIN and TEAM_LEAD, navigating to `/users/:userId`.
- Auto-refresh every 15 seconds.

### 5.5 Personal Dashboard (`/me`)

```
┌──────────────────────────────────────────────────────────────────┐
│  Header: "My Analytics"                  [DateRangeSelector]     │
│                                                        [Refresh] │
├──────────────────────────────────────────────────────────────────┤
│  Last updated: 2:30:15 PM                                        │
├──────────────────────────────────────────────────────────────────┤
│  MetricCards: My Runs, Success Rate, Total Tokens, Total Cost    │
│  ┌──────────────────────────────────────────────────────────────┐│
│  │ "You are #3 of 12 engineers in your organization this       ││
│  │  period."                                                    ││
│  └──────────────────────────────────────────────────────────────┘│
├──────────────────────────────────────────────────────────────────┤
│  My Daily Usage                                                  │
│  ┌──────────────────────────────────────────────────────────────┐│
│  │  Line Chart: Total Runs (single series)                     ││
│  └──────────────────────────────────────────────────────────────┘│
├──────────────────────────────────────────────────────────────────┤
│  Recent Runs                                                     │
│  ┌──────────────────────────────────────────────────────────────┐│
│  │ Table: Time, Agent Type, Status, Duration, Tokens, Cost     ││
│  │ Click row → /runs/:runId                                    ││
│  │ (up to 20 most recent runs)                                 ││
│  └──────────────────────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────────────────┘
```

- Ranking callout shows org-level rank (not team-level).
- Auto-refresh every 15 seconds.

### 5.6 User Dashboard (`/users/:userId`)

```
┌──────────────────────────────────────────────────────────────────┐
│  ← Back                                                         │
│  Header: "{DisplayName} — Analytics"    [DateRangeSelector]      │
├──────────────────────────────────────────────────────────────────┤
│  MetricCards: Total Runs, Success Rate, Total Tokens, Total Cost │
│  ┌──────────────────────────────────────────────────────────────┐│
│  │ "Ranked #3 of 12 engineers in the organization this period."││
│  └──────────────────────────────────────────────────────────────┘│
├──────────────────────────────────────────────────────────────────┤
│  Daily Usage                                                     │
│  ┌──────────────────────────────────────────────────────────────┐│
│  │  Line Chart: Total Runs (single series)                     ││
│  └──────────────────────────────────────────────────────────────┘│
├──────────────────────────────────────────────────────────────────┤
│  Recent Runs                                                     │
│  ┌──────────────────────────────────────────────────────────────┐│
│  │ Table: Time, Agent Type, Status, Duration, Tokens, Cost     ││
│  │ Click row → /runs/:runId                                    ││
│  │ (up to 20 most recent runs)                                 ││
│  └──────────────────────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────────────────┘
```

- Access restricted to ORG_ADMIN and TEAM_LEAD roles.
- Shows a permission error message for unauthorized users.
- Handles 403 and 404 API errors with user-facing messages.

### 5.7 Run Detail (`/runs/:runId`)

```
┌──────────────────────────────────────────────────────────────────┐
│  ← Back                            Run Detail                    │
├─────────────────────────────┬────────────────────────────────────┤
│  Run Metadata               │  Token & Cost Breakdown            │
│  ─────────────              │  ───────────────────               │
│  Run ID: abc-123-def        │  Input Tokens:  350,000            │
│  Agent Type: Code Review    │  Output Tokens: 130,000            │
│  Model: claude-sonnet-4     │  Total Tokens:  480,000            │
│         (v1.0)              │                                    │
│  Status: [SUCCEEDED]        │  Input Cost:  $0.1050             │
│  Started: Jan 15, 8:30 AM   │  Output Cost: $0.0930             │
│  Finished: Jan 15, 8:31 AM  │  Total Cost:  $0.1980             │
│  Duration: 34.0s            │                                    │
├─────────────────────────────┴────────────────────────────────────┤
│  Error Details (only shown if status is FAILED)                  │
│  ┌──────────────────────────────────────────────────────────────┐│
│  │ Category: CONTEXT_LENGTH_EXCEEDED                           ││
│  │ Message: Input exceeded maximum context window of 200k...   ││
│  └──────────────────────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────────────────┘
```

- "Back" button uses browser history navigation (`navigate(-1)`).
- Status displayed using `StatusBadge` component.
- Error details section only renders when status is `FAILED` and `errorCategory` is present.

## 6. Authentication

### 6.1 Auth Flow

- Login via `POST /api/v1/auth/login` with email/password.
- JWT token returned in response, stored in `localStorage`.
- User profile (including `orgId`, `role`, `teams[]`, `orgName`, `displayName`) stored in `localStorage`.
- `AuthContext` provides `user`, `isAuthenticated`, `login()`, and `logout()` to all components.

### 6.2 Token Management

- Axios request interceptor attaches `Authorization: Bearer {token}` header to all API requests.
- Axios response interceptor handles 401 errors: clears `localStorage` and redirects to `/login` (except for login requests themselves, preventing redirect loops).

## 7. Data Fetching Strategy

| Concern | Approach |
|---|---|
| Server state | `useState` + `useEffect` + Axios; parallel `Promise.all` for fetching multiple endpoints per page |
| Auto-refresh | `setInterval` at 15-second intervals on Org, Team, and Personal dashboards |
| Manual refresh | "Refresh" button on each dashboard triggers data re-fetch |
| Last updated | Displayed as locale time string, updated after each successful fetch |
| Loading states | Simple text indicators (e.g., "Loading organization analytics...") |
| Error states | `console.error` for transient errors; inline error messages for permission/not-found errors (User Dashboard) |
| URL state | RunsListPage syncs all filters (from, to, status, team_id, user_id, page) to URL query params via `useSearchParams` |

## 8. API Endpoints Consumed

| Page | Endpoints |
|---|---|
| Login | `POST /auth/login` |
| Org Dashboard | `GET /orgs/{orgId}/analytics/summary`, `GET /orgs/{orgId}/analytics/timeseries`, `GET /orgs/{orgId}/analytics/by-team`, `GET /orgs/{orgId}/analytics/by-agent-type`, `GET /orgs/{orgId}/analytics/top-users` |
| Org Runs List | `GET /orgs/{orgId}/runs`, `GET /orgs/{orgId}/teams`, `GET /orgs/{orgId}/users` |
| Team Dashboard | `GET /teams/{teamId}/analytics/summary`, `GET /teams/{teamId}/analytics/timeseries`, `GET /teams/{teamId}/analytics/by-user`, `GET /orgs/{orgId}/teams` (for name resolution) |
| Personal Dashboard | `GET /users/me/analytics/summary`, `GET /users/me/analytics/timeseries`, `GET /users/me/runs` |
| User Dashboard | `GET /users/{userId}/analytics/summary`, `GET /users/{userId}/analytics/timeseries`, `GET /users/{userId}/runs` |
| Run Detail | `GET /runs/{runId}` |
| Layout (sidebar) | `GET /orgs/{orgId}/teams` (ORG_ADMIN only, for team nav links) |

All endpoints are prefixed with `/api/v1` via Axios base URL configuration.

## 9. Responsive Behavior

| Breakpoint | Layout |
|---|---|
| >= 1024px (lg) | Full layout: sidebar visible, multi-column grids (up to 5 metric cards, 2-column chart sections) |
| 768–1023px (md) | Sidebar visible, metric cards in 3-column grid, charts full-width |
| < 768px (sm) | Sidebar collapsed to hamburger, fixed top header bar, metric cards in 2-column grid, single column layout, tables scroll horizontally |

## 10. Accessibility

- All interactive elements keyboard navigable.
- ARIA label on mobile menu toggle button (`aria-label="Toggle menu"`).
- Form inputs have associated `<label>` elements (Login page).
- Color choices use Tailwind's semantic color scale for sufficient contrast.
- Tables use semantic `<thead>`, `<tbody>`, `<th>` markup.
