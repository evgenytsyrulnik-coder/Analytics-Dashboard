import type {
  LoginResponse,
  AnalyticsSummary,
  TimeseriesData,
  TimeseriesPoint,
  ByTeamData,
  ByAgentTypeData,
  TopUsersData,
  UserSummary,
  RunListData,
  PagedRunList,
  RunDetail,
  OrgUser,
} from '@/types';

// ── Deterministic IDs ───────────────────────────────────────────────────────
const ORG_ID = '00000000-0000-0000-0000-000000000001';
const USER_ID = '00000000-0000-0000-0000-000000000002';
const TEAM_ID_1 = '00000000-0000-0000-0000-000000000010';
const TEAM_ID_2 = '00000000-0000-0000-0000-000000000011';
const RUN_ID_1 = '00000000-0000-0000-0000-000000000100';
const RUN_ID_2 = '00000000-0000-0000-0000-000000000101';
const RUN_ID_3 = '00000000-0000-0000-0000-000000000102';

// ── Default period ──────────────────────────────────────────────────────────
const DEFAULT_PERIOD = { from: '2025-01-01T00:00:00Z', to: '2025-01-31T23:59:59Z' };

// ── Auth ────────────────────────────────────────────────────────────────────
export function buildLoginResponse(
  overrides?: Partial<LoginResponse>,
): LoginResponse {
  return {
    token: 'test-jwt-token-abc123',
    userId: USER_ID,
    orgId: ORG_ID,
    orgName: 'Acme Corp',
    email: 'alice.admin@acme.com',
    displayName: 'Alice Admin',
    role: 'ORG_ADMIN',
    teams: [
      { teamId: TEAM_ID_1, teamName: 'Platform Team' },
      { teamId: TEAM_ID_2, teamName: 'Data Team' },
    ],
    ...overrides,
  };
}

// ── Analytics Summary ───────────────────────────────────────────────────────
export function buildAnalyticsSummary(
  overrides?: Partial<AnalyticsSummary>,
): AnalyticsSummary {
  return {
    orgId: ORG_ID,
    period: DEFAULT_PERIOD,
    totalRuns: 1250,
    succeededRuns: 1100,
    failedRuns: 100,
    cancelledRuns: 30,
    runningRuns: 20,
    successRate: 0.88,
    totalTokens: 15000000,
    totalInputTokens: 9000000,
    totalOutputTokens: 6000000,
    totalCost: '4523.17',
    avgDurationMs: 34500,
    p50DurationMs: 28000,
    p95DurationMs: 72000,
    p99DurationMs: 120000,
    ...overrides,
  };
}

// ── Timeseries ──────────────────────────────────────────────────────────────
function buildTimeseriesPoints(): TimeseriesPoint[] {
  return [
    {
      timestamp: '2025-01-01T00:00:00Z',
      totalRuns: 42,
      succeededRuns: 38,
      failedRuns: 4,
      totalTokens: 500000,
      totalCost: '150.25',
      avgDurationMs: 32000,
    },
    {
      timestamp: '2025-01-02T00:00:00Z',
      totalRuns: 55,
      succeededRuns: 50,
      failedRuns: 5,
      totalTokens: 620000,
      totalCost: '186.00',
      avgDurationMs: 29000,
    },
    {
      timestamp: '2025-01-03T00:00:00Z',
      totalRuns: 38,
      succeededRuns: 35,
      failedRuns: 3,
      totalTokens: 410000,
      totalCost: '123.45',
      avgDurationMs: 35000,
    },
    {
      timestamp: '2025-01-04T00:00:00Z',
      totalRuns: 61,
      succeededRuns: 54,
      failedRuns: 7,
      totalTokens: 730000,
      totalCost: '219.00',
      avgDurationMs: 31000,
    },
    {
      timestamp: '2025-01-05T00:00:00Z',
      totalRuns: 47,
      succeededRuns: 43,
      failedRuns: 4,
      totalTokens: 540000,
      totalCost: '162.10',
      avgDurationMs: 33000,
    },
  ];
}

export function buildTimeseriesData(
  overrides?: Partial<TimeseriesData>,
): TimeseriesData {
  return {
    orgId: ORG_ID,
    granularity: 'DAILY',
    dataPoints: buildTimeseriesPoints(),
    ...overrides,
  };
}

// ── By Team ─────────────────────────────────────────────────────────────────
export function buildByTeamData(
  overrides?: Partial<ByTeamData>,
): ByTeamData {
  return {
    orgId: ORG_ID,
    period: DEFAULT_PERIOD,
    teams: [
      {
        teamId: TEAM_ID_1,
        teamName: 'Platform Team',
        totalRuns: 720,
        totalTokens: 8500000,
        totalCost: '2550.00',
        successRate: 0.912,
        avgDurationMs: 31000,
      },
      {
        teamId: TEAM_ID_2,
        teamName: 'Data Team',
        totalRuns: 530,
        totalTokens: 6500000,
        totalCost: '1973.17',
        successRate: 0.838,
        avgDurationMs: 39000,
      },
    ],
    ...overrides,
  };
}

// ── By Agent Type ───────────────────────────────────────────────────────────
export function buildByAgentTypeData(
  overrides?: Partial<ByAgentTypeData>,
): ByAgentTypeData {
  return {
    orgId: ORG_ID,
    period: DEFAULT_PERIOD,
    agentTypes: [
      {
        agentType: 'CODE_REVIEW',
        displayName: 'Code Review',
        totalRuns: 580,
        totalTokens: 7200000,
        totalCost: '2160.00',
        successRate: 0.924,
        avgDurationMs: 28000,
      },
      {
        agentType: 'DATA_ANALYSIS',
        displayName: 'Data Analysis',
        totalRuns: 420,
        totalTokens: 5100000,
        totalCost: '1530.00',
        successRate: 0.857,
        avgDurationMs: 42000,
      },
      {
        agentType: 'CUSTOMER_SUPPORT',
        displayName: 'Customer Support',
        totalRuns: 250,
        totalTokens: 2700000,
        totalCost: '833.17',
        successRate: 0.816,
        avgDurationMs: 35000,
      },
    ],
    ...overrides,
  };
}

// ── Top Users ───────────────────────────────────────────────────────────────
export function buildTopUsersData(
  overrides?: Partial<TopUsersData>,
): TopUsersData {
  return {
    orgId: ORG_ID,
    sortBy: 'totalRuns',
    users: [
      {
        userId: USER_ID,
        displayName: 'Alice Admin',
        email: 'alice.admin@acme.com',
        teamName: 'Platform Team',
        totalRuns: 320,
        totalTokens: 3800000,
        totalCost: '1140.00',
      },
      {
        userId: '00000000-0000-0000-0000-000000000003',
        displayName: 'Bob Smith',
        email: 'bob.smith@acme.com',
        teamName: 'Data Team',
        totalRuns: 275,
        totalTokens: 3200000,
        totalCost: '960.00',
      },
      {
        userId: '00000000-0000-0000-0000-000000000004',
        displayName: 'Carol Chen',
        email: 'carol.chen@acme.com',
        teamName: 'Platform Team',
        totalRuns: 210,
        totalTokens: 2500000,
        totalCost: '750.00',
      },
    ],
    ...overrides,
  };
}

// ── User Summary ────────────────────────────────────────────────────────────
export function buildUserSummary(
  overrides?: Partial<UserSummary>,
): UserSummary {
  return {
    userId: USER_ID,
    displayName: 'Alice Admin',
    period: DEFAULT_PERIOD,
    totalRuns: 320,
    succeededRuns: 295,
    failedRuns: 25,
    totalTokens: 3800000,
    totalCost: '1140.00',
    avgDurationMs: 30000,
    teamRank: 1,
    teamSize: 8,
    ...overrides,
  };
}

// ── Run List (cursor-based, user runs) ──────────────────────────────────────
export function buildRunListData(
  overrides?: Partial<RunListData>,
): RunListData {
  return {
    runs: [
      {
        runId: RUN_ID_1,
        agentType: 'CODE_REVIEW',
        agentTypeDisplayName: 'Code Review',
        status: 'SUCCEEDED',
        startedAt: '2025-01-15T10:30:00Z',
        finishedAt: '2025-01-15T10:30:45Z',
        durationMs: 45000,
        totalTokens: 12500,
        totalCost: '3.75',
      },
      {
        runId: RUN_ID_2,
        agentType: 'DATA_ANALYSIS',
        agentTypeDisplayName: 'Data Analysis',
        status: 'FAILED',
        startedAt: '2025-01-15T09:15:00Z',
        finishedAt: '2025-01-15T09:16:12Z',
        durationMs: 72000,
        totalTokens: 8200,
        totalCost: '2.46',
      },
      {
        runId: RUN_ID_3,
        agentType: 'CUSTOMER_SUPPORT',
        agentTypeDisplayName: 'Customer Support',
        status: 'RUNNING',
        startedAt: '2025-01-15T11:00:00Z',
        finishedAt: null,
        durationMs: 0,
        totalTokens: 3100,
        totalCost: '0.93',
      },
    ],
    nextCursor: null,
    hasMore: false,
    ...overrides,
  };
}

// ── Paged Run List (org runs) ───────────────────────────────────────────────
export function buildPagedRunList(
  overrides?: Partial<PagedRunList>,
): PagedRunList {
  return {
    runs: [
      {
        runId: RUN_ID_1,
        userId: USER_ID,
        userName: 'Alice Admin',
        teamId: TEAM_ID_1,
        teamName: 'Platform Team',
        agentType: 'CODE_REVIEW',
        agentTypeDisplayName: 'Code Review',
        status: 'SUCCEEDED',
        startedAt: '2025-01-15T10:30:00Z',
        finishedAt: '2025-01-15T10:30:45Z',
        durationMs: 45000,
        totalTokens: 12500,
        totalCost: '3.75',
      },
      {
        runId: RUN_ID_2,
        userId: '00000000-0000-0000-0000-000000000003',
        userName: 'Bob Smith',
        teamId: TEAM_ID_2,
        teamName: 'Data Team',
        agentType: 'DATA_ANALYSIS',
        agentTypeDisplayName: 'Data Analysis',
        status: 'FAILED',
        startedAt: '2025-01-15T09:15:00Z',
        finishedAt: '2025-01-15T09:16:12Z',
        durationMs: 72000,
        totalTokens: 8200,
        totalCost: '2.46',
      },
    ],
    page: 0,
    totalPages: 1,
    totalElements: 2,
    ...overrides,
  };
}

// ── Run Detail ──────────────────────────────────────────────────────────────
export function buildRunDetail(
  overrides?: Partial<RunDetail>,
): RunDetail {
  return {
    runId: RUN_ID_1,
    orgId: ORG_ID,
    teamId: TEAM_ID_1,
    userId: USER_ID,
    agentType: 'CODE_REVIEW',
    agentTypeDisplayName: 'Code Review',
    modelName: 'claude-sonnet',
    modelVersion: '4.0',
    status: 'SUCCEEDED',
    startedAt: '2025-01-15T10:30:00Z',
    finishedAt: '2025-01-15T10:30:45Z',
    durationMs: 45000,
    inputTokens: 8500,
    outputTokens: 4000,
    totalTokens: 12500,
    inputCost: '2.55',
    outputCost: '1.20',
    totalCost: '3.75',
    errorCategory: null,
    errorMessage: null,
    ...overrides,
  };
}

// ── Org User ────────────────────────────────────────────────────────────────
export function buildOrgUser(
  overrides?: Partial<OrgUser>,
): OrgUser {
  return {
    user_id: USER_ID,
    display_name: 'Alice Admin',
    email: 'alice.admin@acme.com',
    ...overrides,
  };
}
