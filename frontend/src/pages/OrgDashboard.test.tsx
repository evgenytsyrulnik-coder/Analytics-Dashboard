import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import OrgDashboard from './OrgDashboard';

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

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/org']}>
      <OrgDashboard />
    </MemoryRouter>,
  );
}

// ── Tests ───────────────────────────────────────────────────────────────────
describe('OrgDashboard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // FE-PG-007
  it('renders loading state initially', () => {
    setupAuth();
    renderPage();

    expect(screen.getByText(/loading organization analytics/i)).toBeInTheDocument();
  });

  // FE-PG-008
  it('renders org name + "Analytics" heading', async () => {
    setupAuth();
    renderPage();

    expect(await screen.findByText('Acme Corporation Analytics')).toBeInTheDocument();
  });

  // FE-PG-009
  it('renders 5 metric cards: Total Runs, Success Rate, Failed Runs, Total Tokens, Total Cost', async () => {
    setupAuth();
    renderPage();

    // "Total Runs" and "Success Rate" also appear in table headers, so use getAllByText
    await waitFor(() => {
      expect(screen.getAllByText('Total Runs').length).toBeGreaterThanOrEqual(1);
    });
    expect(screen.getAllByText('Success Rate').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('Failed Runs')).toBeInTheDocument();
    expect(screen.getAllByText('Total Tokens').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Total Cost').length).toBeGreaterThanOrEqual(1);
  });

  // FE-PG-010
  it('renders "Daily Runs" chart section', async () => {
    setupAuth();
    renderPage();

    expect(await screen.findByText('Daily Runs')).toBeInTheDocument();
  });

  // FE-PG-011
  it('renders "Usage by Team" section with team table', async () => {
    setupAuth();
    renderPage();

    expect(await screen.findByText('Usage by Team')).toBeInTheDocument();
    // "Platform Team" and "Data Team" may appear in multiple tables (by-team + top-users)
    expect(screen.getAllByText('Platform Team').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Data Team').length).toBeGreaterThanOrEqual(1);
  });

  // FE-PG-012
  it('renders "Usage by Agent Type" section with agent type table', async () => {
    setupAuth();
    renderPage();

    expect(await screen.findByText('Usage by Agent Type')).toBeInTheDocument();
    expect(screen.getByText('Code Review')).toBeInTheDocument();
    expect(screen.getByText('Data Analysis')).toBeInTheDocument();
    expect(screen.getByText('Customer Support')).toBeInTheDocument();
  });

  // FE-PG-013
  it('renders "Top Users by Cost" section with user table', async () => {
    setupAuth();
    renderPage();

    expect(await screen.findByText('Top Users by Cost')).toBeInTheDocument();
    expect(screen.getByText('Alice Admin')).toBeInTheDocument();
    expect(screen.getByText('Bob Smith')).toBeInTheDocument();
    expect(screen.getByText('Carol Chen')).toBeInTheDocument();
  });

  // FE-PG-014
  it('shows "Last updated" time text', async () => {
    setupAuth();
    renderPage();

    expect(await screen.findByText(/last updated/i)).toBeInTheDocument();
  });

  // FE-PG-015
  it('has Refresh button', async () => {
    setupAuth();
    renderPage();

    expect(await screen.findByText('Refresh')).toBeInTheDocument();
  });

  // FE-PG-016
  it('has DateRangeSelector', async () => {
    setupAuth();
    renderPage();

    // DateRangeSelector renders preset buttons
    expect(await screen.findByText('Last 7 days')).toBeInTheDocument();
    expect(screen.getByText('Last 30 days')).toBeInTheDocument();
    expect(screen.getByText('Last 90 days')).toBeInTheDocument();
  });

  // FE-PG-017
  it('renders metric card values from API data', async () => {
    setupAuth();
    renderPage();

    // totalRuns = 1250
    expect(await screen.findByText('1,250')).toBeInTheDocument();
    // failedRuns = 100
    expect(screen.getByText('100')).toBeInTheDocument();
  });

  // FE-PG-018
  it('renders "Click on a data point" instruction for chart', async () => {
    setupAuth();
    renderPage();

    expect(await screen.findByText(/click on a data point/i)).toBeInTheDocument();
  });
});
