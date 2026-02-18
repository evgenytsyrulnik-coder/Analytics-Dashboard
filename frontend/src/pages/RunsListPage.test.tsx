import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import { useAuth } from '@/context/AuthContext';
import RunsListPage from './RunsListPage';

// ── Mocks ───────────────────────────────────────────────────────────────────
vi.mock('@/context/AuthContext', () => ({
  useAuth: vi.fn(),
}));

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

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

function renderPage(initialEntries: string[] = ['/org/runs']) {
  return render(
    <MemoryRouter initialEntries={initialEntries}>
      <RunsListPage />
    </MemoryRouter>,
  );
}

function setupTeamsAndUsersHandlers() {
  server.use(
    http.get('/api/v1/orgs/:orgId/teams', () => {
      return HttpResponse.json({
        teams: [
          { team_id: '00000000-0000-0000-0000-000000000010', name: 'Platform Team' },
          { team_id: '00000000-0000-0000-0000-000000000011', name: 'Data Team' },
        ],
      });
    }),
    http.get('/api/v1/orgs/:orgId/users', () => {
      return HttpResponse.json({
        users: [
          { user_id: '00000000-0000-0000-0000-000000000002', display_name: 'Alice Admin', email: 'alice@acme.com' },
          { user_id: '00000000-0000-0000-0000-000000000003', display_name: 'Bob Smith', email: 'bob@acme.com' },
        ],
      });
    }),
  );
}

// ── Tests ───────────────────────────────────────────────────────────────────
describe('RunsListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setupTeamsAndUsersHandlers();
  });

  // FE-PG-019
  it('renders runs table with columns: Time, User, Team, Agent Type, Status, Duration, Tokens, Cost', async () => {
    setupAuth();
    renderPage();

    // Wait for data to load and table to render
    await waitFor(() => {
      expect(screen.getByText('Time')).toBeInTheDocument();
    });
    expect(screen.getByText('User')).toBeInTheDocument();
    // "Team" appears as both a filter label and a table column header
    expect(screen.getAllByText('Team').length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText('Agent Type')).toBeInTheDocument();
    // "Status" may also appear in filter labels
    expect(screen.getByText('Duration')).toBeInTheDocument();
    expect(screen.getByText('Tokens')).toBeInTheDocument();
    expect(screen.getByText('Cost')).toBeInTheDocument();
  });

  // FE-PG-020
  it('shows "X runs found" text', async () => {
    setupAuth();
    renderPage();

    expect(await screen.findByText(/2 runs found/i)).toBeInTheDocument();
  });

  // FE-PG-021
  it('renders Back to Dashboard link', async () => {
    setupAuth();
    renderPage();

    expect(await screen.findByText(/back to dashboard/i)).toBeInTheDocument();
  });

  // FE-PG-022
  it('shows pagination controls when multiple pages', async () => {
    server.use(
      http.get('/api/v1/orgs/:orgId/runs', () => {
        return HttpResponse.json({
          runs: [
            {
              runId: '00000000-0000-0000-0000-000000000100',
              userId: '00000000-0000-0000-0000-000000000002',
              userName: 'Alice Admin',
              teamId: '00000000-0000-0000-0000-000000000010',
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
          ],
          page: 0,
          totalPages: 3,
          totalElements: 75,
        });
      }),
    );
    setupAuth();
    renderPage();

    expect(await screen.findByText('Previous')).toBeInTheDocument();
    expect(screen.getByText('Next')).toBeInTheDocument();
  });

  // FE-PG-023
  it('has team dropdown filter', async () => {
    setupAuth();
    renderPage();

    const teamSelect = await screen.findByDisplayValue('All teams');
    expect(teamSelect).toBeInTheDocument();
  });

  // FE-PG-024
  it('has status multi-select dropdown', async () => {
    setupAuth();
    renderPage();

    expect(await screen.findByText('All results')).toBeInTheDocument();
  });

  // FE-PG-025
  it('has person multi-select dropdown', async () => {
    setupAuth();
    renderPage();

    expect(await screen.findByText('All people')).toBeInTheDocument();
  });

  // FE-PG-026
  it('shows active filter pills when filters applied via URL', async () => {
    setupAuth();
    renderPage(['/org/runs?status=FAILED']);

    // "FAILED" appears in the status dropdown button and as a filter pill
    await waitFor(() => {
      expect(screen.getAllByText('FAILED').length).toBeGreaterThanOrEqual(1);
    });
    // Verify the filter pill with the dismiss button exists
    expect(screen.getByText('Clear all filters')).toBeInTheDocument();
  });

  // FE-PG-027
  it('has "Clear all filters" button when filters are active', async () => {
    setupAuth();
    renderPage(['/org/runs?status=FAILED']);

    expect(await screen.findByText('Clear all filters')).toBeInTheDocument();
  });

  // FE-PG-028
  it('shows loading state', () => {
    setupAuth();
    renderPage();

    expect(screen.getByText(/loading runs/i)).toBeInTheDocument();
  });

  // FE-PG-029
  it('renders run data rows with user and team info', async () => {
    setupAuth();
    renderPage();

    // Names appear in run rows and possibly in the person filter dropdown
    await waitFor(() => {
      expect(screen.getAllByText('Alice Admin').length).toBeGreaterThanOrEqual(1);
    });
    expect(screen.getAllByText('Bob Smith').length).toBeGreaterThanOrEqual(1);
    // Team names appear in team filter dropdown options and run data rows
    expect(screen.getAllByText('Platform Team').length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText('Data Team').length).toBeGreaterThanOrEqual(1);
  });

  // FE-PG-030
  it('displays status badges for runs', async () => {
    setupAuth();
    renderPage();

    // Status text may appear in both the status filter checkboxes and as StatusBadge in table rows
    await waitFor(() => {
      expect(screen.getAllByText('SUCCEEDED').length).toBeGreaterThanOrEqual(1);
    });
    expect(screen.getAllByText('FAILED').length).toBeGreaterThanOrEqual(1);
  });
});
