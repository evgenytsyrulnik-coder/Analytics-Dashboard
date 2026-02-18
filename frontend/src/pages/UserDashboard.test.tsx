import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { useAuth } from '@/context/AuthContext';
import UserDashboard from './UserDashboard';

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
const TARGET_USER_ID = '00000000-0000-0000-0000-000000000002';

function setupAuth(role: 'ORG_ADMIN' | 'TEAM_LEAD' | 'MEMBER' = 'ORG_ADMIN') {
  const user = {
    token: 'test-token',
    userId: '00000000-0000-0000-0000-000000000100',
    orgId: '00000000-0000-0000-0000-000000000001',
    orgName: 'Acme Corporation',
    email: 'admin@acme.com',
    displayName: 'Alice Chen',
    role,
    teams: [{ teamId: '00000000-0000-0000-0000-000000000010', teamName: 'Platform Engineering' }],
  };
  mockUseAuth.mockReturnValue({ user, isAuthenticated: true, login: vi.fn(), logout: vi.fn() });
  return user;
}

function renderPage(userId: string = TARGET_USER_ID) {
  return render(
    <MemoryRouter initialEntries={[`/users/${userId}`]}>
      <Routes>
        <Route path="/users/:userId" element={<UserDashboard />} />
      </Routes>
    </MemoryRouter>,
  );
}

// ── Tests ───────────────────────────────────────────────────────────────────
describe('UserDashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // FE-PG-045
  it('ORG_ADMIN renders user name + "Analytics" heading', async () => {
    setupAuth('ORG_ADMIN');
    renderPage();

    // From buildUserSummary: displayName = 'Alice Admin'
    expect(await screen.findByText(/Alice Admin/)).toBeInTheDocument();
    expect(screen.getByText(/Analytics/)).toBeInTheDocument();
  });

  // FE-PG-046
  it('shows metric cards and rank badge', async () => {
    setupAuth('ORG_ADMIN');
    renderPage();

    expect(await screen.findByText('Total Runs')).toBeInTheDocument();
    expect(screen.getByText('Success Rate')).toBeInTheDocument();
    expect(screen.getByText('Total Tokens')).toBeInTheDocument();
    expect(screen.getByText('Total Cost')).toBeInTheDocument();

    // From buildUserSummary: teamRank=1, teamSize=8
    // The rank badge text: "Ranked #1 of 8 engineers in the organization this period."
    const rankBadge = screen.getByText(/ranked/i);
    expect(rankBadge).toBeInTheDocument();
    expect(rankBadge.textContent).toMatch(/#1/);
    expect(rankBadge.textContent).toMatch(/8/);
  });

  // FE-PG-047
  it('shows 403 permission error message', async () => {
    server.use(
      http.get('/api/v1/users/:userId/analytics/summary', () => {
        return HttpResponse.json({ detail: 'Forbidden' }, { status: 403 });
      }),
      http.get('/api/v1/users/:userId/analytics/timeseries', () => {
        return HttpResponse.json({ detail: 'Forbidden' }, { status: 403 });
      }),
      http.get('/api/v1/users/:userId/runs', () => {
        return HttpResponse.json({ detail: 'Forbidden' }, { status: 403 });
      }),
    );
    setupAuth('TEAM_LEAD');
    renderPage();

    expect(await screen.findByText(/you do not have permission to view this user/i)).toBeInTheDocument();
  });

  // FE-PG-048
  it('shows 404 user not found message', async () => {
    server.use(
      http.get('/api/v1/users/:userId/analytics/summary', () => {
        return HttpResponse.json({ detail: 'Not found' }, { status: 404 });
      }),
      http.get('/api/v1/users/:userId/analytics/timeseries', () => {
        return HttpResponse.json({ detail: 'Not found' }, { status: 404 });
      }),
      http.get('/api/v1/users/:userId/runs', () => {
        return HttpResponse.json({ detail: 'Not found' }, { status: 404 });
      }),
    );
    setupAuth('ORG_ADMIN');
    renderPage('00000000-0000-0000-0000-000000099999');

    expect(await screen.findByText(/user not found/i)).toBeInTheDocument();
  });

  // FE-PG-049
  it('has Back button', async () => {
    setupAuth('ORG_ADMIN');
    renderPage();

    expect(await screen.findByText(/back/i)).toBeInTheDocument();
  });

  // FE-PG-050
  it('MEMBER (non-admin/non-lead) sees permission error', () => {
    setupAuth('MEMBER');
    renderPage();

    expect(screen.getByText(/you do not have permission to view this page/i)).toBeInTheDocument();
  });
});
