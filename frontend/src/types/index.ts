export interface LoginResponse {
  token: string;
  userId: string;
  orgId: string;
  orgName: string;
  email: string;
  displayName: string;
  role: 'ORG_ADMIN' | 'TEAM_LEAD' | 'MEMBER';
  teams: TeamInfo[];
}

export interface TeamInfo {
  teamId: string;
  teamName: string;
}

export interface AnalyticsSummary {
  orgId: string;
  period: { from: string; to: string };
  totalRuns: number;
  succeededRuns: number;
  failedRuns: number;
  cancelledRuns: number;
  runningRuns: number;
  successRate: number;
  totalTokens: number;
  totalInputTokens: number;
  totalOutputTokens: number;
  totalCost: string;
  avgDurationMs: number;
  p50DurationMs: number;
  p95DurationMs: number;
  p99DurationMs: number;
}

export interface TimeseriesData {
  orgId: string;
  granularity: string;
  dataPoints: TimeseriesPoint[];
}

export interface TimeseriesPoint {
  timestamp: string;
  totalRuns: number;
  succeededRuns: number;
  failedRuns: number;
  totalTokens: number;
  totalCost: string;
  avgDurationMs: number;
}

export interface ByTeamData {
  orgId: string;
  period: { from: string; to: string };
  teams: TeamBreakdown[];
}

export interface TeamBreakdown {
  teamId: string;
  teamName: string;
  totalRuns: number;
  totalTokens: number;
  totalCost: string;
  successRate: number;
  avgDurationMs: number;
}

export interface ByAgentTypeData {
  orgId: string;
  period: { from: string; to: string };
  agentTypes: AgentTypeBreakdown[];
}

export interface AgentTypeBreakdown {
  agentType: string;
  displayName: string;
  totalRuns: number;
  totalTokens: number;
  totalCost: string;
  successRate: number;
  avgDurationMs: number;
}

export interface TopUsersData {
  orgId: string;
  sortBy: string;
  users: UserMetric[];
}

export interface UserMetric {
  userId: string;
  displayName: string;
  email: string;
  teamName: string;
  totalRuns: number;
  totalTokens: number;
  totalCost: string;
}

export interface UserSummary {
  userId: string;
  displayName: string;
  period: { from: string; to: string };
  totalRuns: number;
  succeededRuns: number;
  failedRuns: number;
  totalTokens: number;
  totalCost: string;
  avgDurationMs: number;
  teamRank: number;
  teamSize: number;
}

export interface RunListData {
  runs: RunSummary[];
  nextCursor: string | null;
  hasMore: boolean;
}

export interface RunSummary {
  runId: string;
  agentType: string;
  agentTypeDisplayName: string;
  status: string;
  startedAt: string;
  finishedAt: string | null;
  durationMs: number;
  totalTokens: number;
  totalCost: string;
}

export interface RunDetail {
  runId: string;
  orgId: string;
  teamId: string;
  userId: string;
  agentType: string;
  agentTypeDisplayName: string;
  modelName: string;
  modelVersion: string;
  status: string;
  startedAt: string;
  finishedAt: string | null;
  durationMs: number;
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
  inputCost: string;
  outputCost: string;
  totalCost: string;
  errorCategory: string | null;
  errorMessage: string | null;
}
