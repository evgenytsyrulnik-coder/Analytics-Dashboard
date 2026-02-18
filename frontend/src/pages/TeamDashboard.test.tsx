import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { useAuth } from '@/context/AuthContext';
import TeamDashboard from './TeamDashboard';

// ── Mocks ───────────────────────────────────────────────────────────────────
vi.mock('@/context/AuthContext', () => ({
  useAuth: vi.fn(),
}));

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

vi.mock('recharts', () => ({
  ResponsiveContainer: ({ children }: any) => <div data-testid="responsive-container">{children}</div>,
  LineChart: ({ children }: any) => <div data-testid="line-chart">{children}</div>,
  Line: () => null,
  BarChart: ({ children }: any) => <div data-testid="bar-chart">{children}</div>,
  Bar: () => null,
  PieChart: ({ children }: any) => <div data-testid="pie-chart">{children}</div>,
  Pie: () => null,
  Cell: () => null,
  Legend: () => null,
  XAxis: () => null,
  YAxis: () => null,
  CartesianGrid: () => null,
  Tooltip: () => null,
}));

const mockUseAuth = vi.mocked(useAuth);

// ── Helpers ─────────────────────────────────────────────────────────────────
const TEAM_ID = '00000000-0000-0000-0000-000000000010';

function setupAuth(role: 'ORG_ADMIN' | 'TEAM_LEAD' | 'MEMBER' = 'ORG_ADMIN') {
  const user = {
    token: 'test-token',
    userId: '00000000-0000-0000-0000-000000000100',
    orgId: '00000000-0000-0000-0000-000000000001',
    orgName: 'Acme Corporation',
    email: 'admin@acme.com',
    displayName: 'Alice Chen',
    role,
    teams: [{ teamId: TEAM_ID, teamName: 'Platform Engineering' }],
  };
  mockUseAuth.mockReturnValue({ user, isAuthenticated: true, login: vi.fn(), logout: vi.fn() });
  return user;
}

function setupByUserHandler() {
  server.use(
    http.get('/api/v1/teams/:teamId/analytics/by-user', () => {
      return HttpResponse.json({
        orgId: '00000000-0000-0000-0000-000000000001',
        period: { from: '2025-01-01', to: '2025-01-31' },
        teams: [
          {
            teamId: '00000000-0000-0000-0000-000000000002',
            teamName: 'Alice Admin',
            totalRuns: 320,
            totalTokens: 3800000,
            totalCost: '1140.00',
            successRate: 0.92,
            avgDurationMs: 30000,
          },
          {
            teamId: '00000000-0000-0000-0000-000000000003',
            teamName: 'Bob Smith',
            totalRuns: 275,
            totalTokens: 3200000,
            totalCost: '960.00',
            successRate: 0.85,
            avgDurationMs: 35000,
          },
        ],
      });
    }),
  );
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={[`/teams/${TEAM_ID}`]}>
      <Routes>
        <Route path="/teams/:teamId" element={<TeamDashboard />} />
      </Routes>
    </MemoryRouter>,
  );
}

// ── Tests ───────────────────────────────────────────────────────────────────
describe('TeamDashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setupByUserHandler();
  });

  // FE-PG-031
  it('renders "Team: {name}" header', async () => {
    setupAuth();
    renderPage();

    expect(await screen.findByText(/team: platform engineering/i)).toBeInTheDocument();
  });

  // FE-PG-032
  it('renders 5 metric cards', async () => {
    setupAuth();
    renderPage();

    // "Success Rate" also appears in the user table header, so use getAllByText
    await waitFor(() => {
      expect(screen.getByText('Total Runs')).toBeInTheDocument();
    });
    expect(screen.getAllByText('Success Rate').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('Failed Runs')).toBeInTheDocument();
    expect(screen.getByText('Total Tokens')).toBeInTheDocument();
    expect(screen.getByText('Total Cost')).toBeInTheDocument();
  });

  // FE-PG-033
  it('renders Daily Runs chart section', async () => {
    setupAuth();
    renderPage();

    expect(await screen.findByText('Daily Runs')).toBeInTheDocument();
  });

  // FE-PG-034
  it('renders "Usage by User" table', async () => {
    setupAuth();
    renderPage();

    expect(await screen.findByText('Usage by User')).toBeInTheDocument();
    expect(screen.getByText('Alice Admin')).toBeInTheDocument();
    expect(screen.getByText('Bob Smith')).toBeInTheDocument();
  });

  // FE-PG-035
  it('shows "Last updated" text', async () => {
    setupAuth();
    renderPage();

    expect(await screen.findByText(/last updated/i)).toBeInTheDocument();
  });

  // FE-PG-036
  it('has Refresh button and DateRangeSelector', async () => {
    setupAuth();
    renderPage();

    expect(await screen.findByText('Refresh')).toBeInTheDocument();
    expect(screen.getByText('Last 7 days')).toBeInTheDocument();
    expect(screen.getByText('Last 30 days')).toBeInTheDocument();
  });

  // FE-PG-037
  it('ORG_ADMIN sees clickable chart data points message', async () => {
    setupAuth('ORG_ADMIN');
    renderPage();

    expect(await screen.findByText(/click on a data point/i)).toBeInTheDocument();
  });
});
