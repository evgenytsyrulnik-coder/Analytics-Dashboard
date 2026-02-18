import { http, HttpResponse } from 'msw';
import {
  buildLoginResponse,
  buildAnalyticsSummary,
  buildTimeseriesData,
  buildByTeamData,
  buildByAgentTypeData,
  buildTopUsersData,
  buildUserSummary,
  buildRunListData,
  buildPagedRunList,
  buildRunDetail,
  buildOrgUser,
} from '../factories';

export const handlers = [
  // ── Auth ──────────────────────────────────────────────────────────────
  http.post('/api/v1/auth/login', async ({ request }) => {
    const body = (await request.json()) as { email: string; password: string };

    if (body.email === 'bad@example.com') {
      return HttpResponse.json(
        { error: 'Invalid credentials' },
        { status: 401 },
      );
    }

    return HttpResponse.json(buildLoginResponse());
  }),

  // ── Org Analytics ─────────────────────────────────────────────────────
  http.get('/api/v1/orgs/:orgId/analytics/summary', () => {
    return HttpResponse.json(buildAnalyticsSummary());
  }),

  http.get('/api/v1/orgs/:orgId/analytics/timeseries', () => {
    return HttpResponse.json(buildTimeseriesData());
  }),

  http.get('/api/v1/orgs/:orgId/analytics/by-team', () => {
    return HttpResponse.json(buildByTeamData());
  }),

  http.get('/api/v1/orgs/:orgId/analytics/by-agent-type', () => {
    return HttpResponse.json(buildByAgentTypeData());
  }),

  http.get('/api/v1/orgs/:orgId/analytics/top-users', () => {
    return HttpResponse.json(buildTopUsersData());
  }),

  // ── Org Runs ──────────────────────────────────────────────────────────
  http.get('/api/v1/orgs/:orgId/runs', () => {
    return HttpResponse.json(buildPagedRunList());
  }),

  // ── Org Resources ─────────────────────────────────────────────────────
  http.get('/api/v1/orgs/:orgId/teams', () => {
    return HttpResponse.json({
      teams: [
        { team_id: '00000000-0000-0000-0000-000000000010', name: 'Platform Engineering' },
        { team_id: '00000000-0000-0000-0000-000000000011', name: 'Data Science' },
      ],
    });
  }),

  http.get('/api/v1/orgs/:orgId/users', () => {
    return HttpResponse.json({
      users: [
        buildOrgUser(),
        buildOrgUser({
          user_id: '00000000-0000-0000-0000-000000000003',
          display_name: 'Bob Smith',
          email: 'bob.smith@acme.com',
        }),
      ],
    });
  }),

  http.get('/api/v1/orgs/:orgId/agent-types', () => {
    return HttpResponse.json({
      agent_types: [
        { slug: 'code_review', display_name: 'Code Review' },
        { slug: 'data_analysis', display_name: 'Data Analysis' },
        { slug: 'customer_support', display_name: 'Customer Support' },
      ],
    });
  }),

  http.get('/api/v1/orgs/:orgId/budgets', () => {
    return HttpResponse.json({
      orgId: '00000000-0000-0000-0000-000000000001',
      monthlyBudget: '10000.00',
      currentSpend: '4523.17',
      remainingBudget: '5476.83',
      utilizationPercent: 45.23,
    });
  }),

  // ── Team Analytics ────────────────────────────────────────────────────
  http.get('/api/v1/teams/:teamId/analytics/summary', () => {
    return HttpResponse.json(
      buildAnalyticsSummary({
        orgId: '00000000-0000-0000-0000-000000000001',
      }),
    );
  }),

  http.get('/api/v1/teams/:teamId/analytics/timeseries', () => {
    return HttpResponse.json(buildTimeseriesData());
  }),

  http.get('/api/v1/teams/:teamId/analytics/by-user', () => {
    return HttpResponse.json(buildByTeamData());
  }),

  // ── Current User Analytics ────────────────────────────────────────────
  http.get('/api/v1/users/me/analytics/summary', () => {
    return HttpResponse.json(buildUserSummary());
  }),

  http.get('/api/v1/users/me/analytics/timeseries', () => {
    return HttpResponse.json(buildTimeseriesData());
  }),

  http.get('/api/v1/users/me/runs', () => {
    return HttpResponse.json(buildRunListData());
  }),

  // ── Specific User Analytics ───────────────────────────────────────────
  http.get('/api/v1/users/:userId/analytics/summary', () => {
    return HttpResponse.json(buildUserSummary());
  }),

  http.get('/api/v1/users/:userId/analytics/timeseries', () => {
    return HttpResponse.json(buildTimeseriesData());
  }),

  http.get('/api/v1/users/:userId/runs', () => {
    return HttpResponse.json(buildRunListData());
  }),

  // ── Run Detail ────────────────────────────────────────────────────────
  http.get('/api/v1/runs/:runId', ({ params }) => {
    const { runId } = params;
    return HttpResponse.json(
      buildRunDetail({ runId: runId as string }),
    );
  }),
];
