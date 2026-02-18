import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import App from '@/App';
import type { LoginResponse } from '@/types';
import type { Mock } from 'vitest';

// ── Mock all page components ────────────────────────────────────────────────
vi.mock('@/pages/LoginPage', () => ({
  default: () => <div data-testid="login-page">LoginPage</div>,
}));

vi.mock('@/pages/OrgDashboard', () => ({
  default: () => <div data-testid="org-dashboard">OrgDashboard</div>,
}));

vi.mock('@/pages/TeamDashboard', () => ({
  default: () => <div data-testid="team-dashboard">TeamDashboard</div>,
}));

vi.mock('@/pages/PersonalDashboard', () => ({
  default: () => <div data-testid="personal-dashboard">PersonalDashboard</div>,
}));

vi.mock('@/pages/UserDashboard', () => ({
  default: () => <div data-testid="user-dashboard">UserDashboard</div>,
}));

vi.mock('@/pages/RunDetail', () => ({
  default: () => <div data-testid="run-detail">RunDetail</div>,
}));

vi.mock('@/pages/RunsListPage', () => ({
  default: () => <div data-testid="runs-list-page">RunsListPage</div>,
}));

// ── Mock Layout to render Outlet ────────────────────────────────────────────
vi.mock('@/components/Layout', () => ({
  default: () => {
    const { Outlet } = require('react-router-dom');
    return (
      <div data-testid="layout">
        <Outlet />
      </div>
    );
  },
}));

// ── Mock useAuth ────────────────────────────────────────────────────────────
vi.mock('@/context/AuthContext', () => ({
  useAuth: vi.fn(),
}));

const mockedUseAuth = useAuth as Mock;

// ── Helper: build a mock user ───────────────────────────────────────────────
function buildMockUser(overrides?: Partial<LoginResponse>): LoginResponse {
  return {
    token: 'test-jwt-token-abc123',
    userId: '00000000-0000-0000-0000-000000000002',
    orgId: '00000000-0000-0000-0000-000000000001',
    orgName: 'Acme Corp',
    email: 'alice.admin@acme.com',
    displayName: 'Alice Admin',
    role: 'ORG_ADMIN',
    teams: [
      { teamId: '00000000-0000-0000-0000-000000000010', teamName: 'Platform Team' },
      { teamId: '00000000-0000-0000-0000-000000000011', teamName: 'Data Team' },
    ],
    ...overrides,
  };
}

// ── Helper: render App at a given route ─────────────────────────────────────
function renderApp(initialRoute: string) {
  return render(
    <MemoryRouter initialEntries={[initialRoute]}>
      <App />
    </MemoryRouter>,
  );
}

// ── Test Suite ──────────────────────────────────────────────────────────────
describe('App Routing & Authorization', () => {
  // FE-RT-001: ORG_ADMIN navigates to / → redirected to /org
  it('FE-RT-001: redirects ORG_ADMIN from / to /org (OrgDashboard)', () => {
    const user = buildMockUser({ role: 'ORG_ADMIN' });
    mockedUseAuth.mockReturnValue({
      user,
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn(),
    });

    renderApp('/');

    expect(screen.getByTestId('org-dashboard')).toBeInTheDocument();
  });

  // FE-RT-002: TEAM_LEAD navigates to / → redirected to /teams/:firstTeamId
  it('FE-RT-002: redirects TEAM_LEAD from / to /teams/:firstTeamId (TeamDashboard)', () => {
    const user = buildMockUser({
      role: 'TEAM_LEAD',
      email: 'lead@acme.com',
      displayName: 'Raj Patel',
      teams: [
        { teamId: 'team-lead-001', teamName: 'Platform Team' },
        { teamId: 'team-lead-002', teamName: 'Data Team' },
      ],
    });
    mockedUseAuth.mockReturnValue({
      user,
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn(),
    });

    renderApp('/');

    expect(screen.getByTestId('team-dashboard')).toBeInTheDocument();
  });

  // FE-RT-003: MEMBER navigates to / → redirected to /me
  it('FE-RT-003: redirects MEMBER from / to /me (PersonalDashboard)', () => {
    const user = buildMockUser({
      role: 'MEMBER',
      email: 'member@acme.com',
      displayName: 'Liam Brown',
    });
    mockedUseAuth.mockReturnValue({
      user,
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn(),
    });

    renderApp('/');

    expect(screen.getByTestId('personal-dashboard')).toBeInTheDocument();
  });

  // FE-RT-004: Unauthenticated user visits /org → redirected to /login
  it('FE-RT-004: redirects unauthenticated user from /org to /login', () => {
    mockedUseAuth.mockReturnValue({
      user: null,
      isAuthenticated: false,
      login: vi.fn(),
      logout: vi.fn(),
    });

    renderApp('/org');

    expect(screen.getByTestId('login-page')).toBeInTheDocument();
    expect(screen.queryByTestId('org-dashboard')).not.toBeInTheDocument();
  });

  // FE-RT-005: Authenticated user visits /login → renders LoginPage
  // (LoginPage internally checks isAuthenticated and redirects, but
  //  since we mocked LoginPage, we just confirm it renders)
  it('FE-RT-005: renders LoginPage when authenticated user visits /login', () => {
    const user = buildMockUser({ role: 'ORG_ADMIN' });
    mockedUseAuth.mockReturnValue({
      user,
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn(),
    });

    renderApp('/login');

    expect(screen.getByTestId('login-page')).toBeInTheDocument();
  });

  // FE-RT-006: ORG_ADMIN can access /runs/:runId
  it('FE-RT-006: ORG_ADMIN can access /runs/:runId (RunDetail)', () => {
    const user = buildMockUser({ role: 'ORG_ADMIN' });
    mockedUseAuth.mockReturnValue({
      user,
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn(),
    });

    renderApp('/runs/some-run-id');

    expect(screen.getByTestId('run-detail')).toBeInTheDocument();
  });

  // FE-RT-007: TEAM_LEAD can access /runs/:runId
  it('FE-RT-007: TEAM_LEAD can access /runs/:runId (RunDetail)', () => {
    const user = buildMockUser({ role: 'TEAM_LEAD' });
    mockedUseAuth.mockReturnValue({
      user,
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn(),
    });

    renderApp('/runs/some-run-id');

    expect(screen.getByTestId('run-detail')).toBeInTheDocument();
  });

  // FE-RT-008: MEMBER can access /runs/:runId
  it('FE-RT-008: MEMBER can access /runs/:runId (RunDetail)', () => {
    const user = buildMockUser({ role: 'MEMBER' });
    mockedUseAuth.mockReturnValue({
      user,
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn(),
    });

    renderApp('/runs/some-run-id');

    expect(screen.getByTestId('run-detail')).toBeInTheDocument();
  });

  // FE-RT-009: All roles can access /me
  it('FE-RT-009: all roles can access /me (PersonalDashboard)', () => {
    const roles = ['ORG_ADMIN', 'TEAM_LEAD', 'MEMBER'] as const;

    for (const role of roles) {
      const user = buildMockUser({ role });
      mockedUseAuth.mockReturnValue({
        user,
        isAuthenticated: true,
        login: vi.fn(),
        logout: vi.fn(),
      });

      const { unmount } = renderApp('/me');
      expect(screen.getByTestId('personal-dashboard')).toBeInTheDocument();
      unmount();
    }
  });

  // FE-RT-010: Unknown route → redirected to /
  it('FE-RT-010: unknown route redirects to / (then role-based redirect)', () => {
    const user = buildMockUser({ role: 'ORG_ADMIN' });
    mockedUseAuth.mockReturnValue({
      user,
      isAuthenticated: true,
      login: vi.fn(),
      logout: vi.fn(),
    });

    renderApp('/some/unknown/path');

    // The catch-all * route redirects to /, which triggers RoleRedirect,
    // which for ORG_ADMIN redirects to /org
    expect(screen.getByTestId('org-dashboard')).toBeInTheDocument();
  });
});
