# Product Requirements Document — Agent Analytics Dashboard

## 1. Overview

The Agent Analytics Dashboard is a customer-facing, organizational-level analytics product. It provides engineering organizations with visibility into how their teams use cloud-based AI agents — including usage volume, cost, performance, success rates, and trends over time.

Customers are companies whose engineers run AI agents in the cloud via our platform. Each company (organization) has multiple users (engineers). The dashboard surfaces aggregated and drill-down analytics so engineering leaders and individual contributors can understand and optimize their agent usage.

## 2. Personas

| Persona | Role | Primary Goals |
|---|---|---|
| **Org Admin** | Engineering Director / VP Eng | Monitor org-wide spend, enforce budgets, view team-level breakdowns |
| **Team Lead** | Tech Lead / Manager | Track team usage, identify heavy consumers, compare agent effectiveness |
| **Engineer** | IC Developer | View personal usage, debug slow/failed agent runs, optimize workflows |

## 3. Core Concepts

- **Organization**: A customer account. All data is scoped to an organization. Organizations are multi-tenant and fully isolated.
- **Team**: A subdivision within an organization. An organization has one or more teams.
- **User**: An engineer belonging to an organization and optionally one or more teams.
- **Agent Run**: A single invocation of an AI agent in the cloud. This is the atomic unit of analytics. Each run has metadata including start time, end time, status, token counts, cost, model used, and the user/team that initiated it.
- **Agent Type**: A named category of agent (e.g., "Code Review Agent", "Test Generator Agent", "Debugging Agent"). Organizations may define custom agent types.

## 4. Functional Requirements

### 4.1 Authentication & Authorization

| ID | Requirement |
|---|---|
| AUTH-1 | Users authenticate via the platform's existing OAuth 2.0 / SSO integration. The dashboard does not manage its own credentials. |
| AUTH-2 | Three roles govern access: `ORG_ADMIN`, `TEAM_LEAD`, `MEMBER`. |
| AUTH-3 | `ORG_ADMIN` can view all data within the organization. |
| AUTH-4 | `TEAM_LEAD` can view data for their own teams and their own personal data. |
| AUTH-5 | `MEMBER` can view only their own personal data and anonymized org-wide aggregates. |
| AUTH-6 | Role mappings are managed externally; the dashboard reads roles from JWT claims. |

### 4.2 Organization Dashboard (Org Admin view)

| ID | Requirement |
|---|---|
| ORG-1 | Display total agent runs, total tokens consumed, and total cost for a selected date range. |
| ORG-2 | Show a time-series chart of daily agent runs, tokens, and cost. |
| ORG-3 | Show a breakdown of usage by team (table + bar chart). |
| ORG-4 | Show a breakdown of usage by agent type (table + pie chart). |
| ORG-5 | Show top 10 users by run count, token usage, and cost. |
| ORG-6 | Show agent run success rate (succeeded / total) over time. |
| ORG-7 | Show average agent run duration over time. |
| ORG-8 | Support date range selection: last 7 days, last 30 days, last 90 days, custom range (max 1 year). |
| ORG-9 | Support filtering by team, agent type, and status. |
| ORG-10 | Allow exporting the current view as CSV. |

### 4.3 Team Dashboard (Team Lead view)

| ID | Requirement |
|---|---|
| TEAM-1 | Display the same metric cards as the org dashboard, scoped to the selected team. |
| TEAM-2 | Show a breakdown of usage by individual user within the team. |
| TEAM-3 | Show a breakdown of usage by agent type within the team. |
| TEAM-4 | Support the same date range and filter controls as the org dashboard. |
| TEAM-5 | Allow a team lead with multiple teams to switch between teams. |

### 4.4 Personal Dashboard (Engineer / Member view)

| ID | Requirement |
|---|---|
| USER-1 | Display total agent runs, tokens, and cost for the authenticated user. |
| USER-2 | Show a time-series chart of the user's daily usage. |
| USER-3 | Show a table of recent agent runs with: timestamp, agent type, status, duration, tokens, cost. |
| USER-4 | Allow clicking a run to view run details: full metadata, input/output token breakdown, error message (if failed). |
| USER-5 | Support the same date range controls. |
| USER-6 | Show the user's rank within their team (e.g., "You are the 3rd most active user on your team this month"). |

### 4.5 Agent Run Detail View

| ID | Requirement |
|---|---|
| RUN-1 | Display full metadata: run ID, agent type, user, team, start time, end time, duration, status. |
| RUN-2 | Display token breakdown: input tokens, output tokens, total tokens. |
| RUN-3 | Display cost breakdown: input cost, output cost, total cost. |
| RUN-4 | Display model name and model version used. |
| RUN-5 | If the run failed, display the error category and error message. |
| RUN-6 | Accessible to the user who initiated the run, their team lead, and org admins. |

### 4.6 Usage Alerts & Budgets

| ID | Requirement |
|---|---|
| ALERT-1 | Org admins can set a monthly cost budget for the organization. |
| ALERT-2 | Org admins can set a monthly cost budget per team. |
| ALERT-3 | The system sends notifications (in-app + email) at 50%, 80%, and 100% of budget thresholds. |
| ALERT-4 | Org admins can configure custom alert thresholds. |
| ALERT-5 | Display a budget utilization gauge on the org and team dashboards. |

### 4.7 Data Freshness & Real-Time

| ID | Requirement |
|---|---|
| FRESH-1 | Dashboard data must be no more than 5 minutes stale under normal operation. |
| FRESH-2 | The UI displays a "Last updated" timestamp. |
| FRESH-3 | Users can manually trigger a data refresh. |
| FRESH-4 | Active agent runs (in-progress) are shown with a "running" indicator on the personal dashboard. |

## 5. Out of Scope (v1)

- Per-agent-run log streaming or replay.
- Configuring or launching agent runs from the dashboard.
- Billing / invoicing (the dashboard shows cost analytics, not invoices).
- Comparison across organizations.
- Public or shared dashboard links.

## 6. Success Metrics

| Metric | Target |
|---|---|
| Dashboard page load time (p95) | < 2 seconds |
| Data freshness | ≤ 5 minutes |
| Adoption (% of org admins who view dashboard weekly) | > 60% within 3 months |
| CSV export usage | > 20% of org admins use export monthly |
