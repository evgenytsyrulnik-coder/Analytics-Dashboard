import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import PersonalDashboard from './PersonalDashboard';

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
function setupAuth(role: 'ORG_ADMIN' | 'TEAM_LEAD' | 'MEMBER' = 'MEMBER') {
  const user = {
    token: 'test-token',
    userId: '00000000-0000-0000-0000-000000000100',
    orgId: '00000000-0000-0000-0000-000000000001',
    orgName: 'Acme Corporation',
    email: 'member1@acme.com',
    displayName: 'Liam Brown',
    role,
    teams: [{ teamId: '00000000-0000-0000-0000-000000000010', teamName: 'Platform Engineering' }],
  };
  mockUseAuth.mockReturnValue({ user, isAuthenticated: true, login: vi.fn(), logout: vi.fn() });
  return user;
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/me']}>
      <PersonalDashboard />
    </MemoryRouter>,
  );
}

// ── Tests ───────────────────────────────────────────────────────────────────
describe('PersonalDashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // FE-PG-038
  it('renders "My Analytics" heading', async () => {
    setupAuth();
    renderPage();

    expect(await screen.findByText('My Analytics')).toBeInTheDocument();
  });

  // FE-PG-039
  it('renders 4 metric cards: My Runs, Success Rate, Total Tokens, Total Cost', async () => {
    setupAuth();
    renderPage();

    expect(await screen.findByText('My Runs')).toBeInTheDocument();
    expect(screen.getByText('Success Rate')).toBeInTheDocument();
    expect(screen.getByText('Total Tokens')).toBeInTheDocument();
    expect(screen.getByText('Total Cost')).toBeInTheDocument();
  });

  // FE-PG-040
  it('shows team rank badge "You are #N of M engineers..."', async () => {
    setupAuth();
    renderPage();

    // From buildUserSummary: teamRank=1, teamSize=8
    // The rank badge text: "You are #1 of 8 engineers in your organization this period."
    const rankBadge = await screen.findByText(/you are/i);
    expect(rankBadge).toBeInTheDocument();
    expect(rankBadge.textContent).toMatch(/#1/);
    expect(rankBadge.textContent).toMatch(/8/);
    expect(rankBadge.textContent).toMatch(/engineers/i);
  });

  // FE-PG-041
  it('renders "My Daily Usage" chart section', async () => {
    setupAuth();
    renderPage();

    expect(await screen.findByText('My Daily Usage')).toBeInTheDocument();
  });

  // FE-PG-042
  it('renders "Recent Runs" table', async () => {
    setupAuth();
    renderPage();

    expect(await screen.findByText('Recent Runs')).toBeInTheDocument();
    // From buildRunListData: runs include Code Review, Data Analysis, Customer Support
    expect(screen.getByText('Code Review')).toBeInTheDocument();
    expect(screen.getByText('Data Analysis')).toBeInTheDocument();
    expect(screen.getByText('Customer Support')).toBeInTheDocument();
  });

  // FE-PG-043
  it('has Refresh button and DateRangeSelector', async () => {
    setupAuth();
    renderPage();

    expect(await screen.findByText('Refresh')).toBeInTheDocument();
    expect(screen.getByText('Last 7 days')).toBeInTheDocument();
    expect(screen.getByText('Last 30 days')).toBeInTheDocument();
    expect(screen.getByText('Last 90 days')).toBeInTheDocument();
  });

  // FE-PG-044
  it('shows loading state initially', () => {
    setupAuth();
    renderPage();

    expect(screen.getByText(/loading your analytics/i)).toBeInTheDocument();
  });
});
