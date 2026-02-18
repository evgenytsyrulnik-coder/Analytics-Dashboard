import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { server } from '@/test/mocks/server';
import Layout from './Layout';

// Mock the AuthContext module so we can control useAuth return values per test
const mockLogout = vi.fn();
const mockUseAuth = vi.fn();

vi.mock('@/context/AuthContext', () => ({
  useAuth: (...args: unknown[]) => mockUseAuth(...args),
}));

// Mock the navigate function
const mockNavigate = vi.fn();

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

function renderLayout() {
  return render(
    <MemoryRouter>
      <Layout />
    </MemoryRouter>,
  );
}

describe('Layout', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('navigation links by role', () => {
    it('shows Organization link, team links, and My Dashboard for ORG_ADMIN', async () => {
      // Set up the teams API response for ORG_ADMIN
      server.use(
        http.get('/api/v1/orgs/:orgId/teams', () => {
          return HttpResponse.json({
            teams: [
              { team_id: 'team-1', name: 'Alpha Team' },
              { team_id: 'team-2', name: 'Beta Team' },
            ],
          });
        }),
      );

      mockUseAuth.mockReturnValue({
        user: {
          token: 'test-token',
          userId: 'user-1',
          orgId: 'org-1',
          orgName: 'Acme Corp',
          email: 'admin@acme.com',
          displayName: 'Admin User',
          role: 'ORG_ADMIN',
          teams: [
            { teamId: 'team-1', teamName: 'Alpha Team' },
          ],
        },
        logout: mockLogout,
      });

      renderLayout();

      // Organization link should be present immediately
      expect(screen.getByText('Organization')).toBeInTheDocument();
      expect(screen.getByText('My Dashboard')).toBeInTheDocument();

      // Wait for teams fetched via API to appear
      await waitFor(() => {
        expect(screen.getByText('Alpha Team')).toBeInTheDocument();
        expect(screen.getByText('Beta Team')).toBeInTheDocument();
      });
    });

    it('shows team links and My Dashboard but no Organization link for TEAM_LEAD', () => {
      mockUseAuth.mockReturnValue({
        user: {
          token: 'test-token',
          userId: 'user-2',
          orgId: 'org-1',
          orgName: 'Acme Corp',
          email: 'lead@acme.com',
          displayName: 'Team Lead',
          role: 'TEAM_LEAD',
          teams: [
            { teamId: 'team-1', teamName: 'Alpha Team' },
            { teamId: 'team-3', teamName: 'Gamma Team' },
          ],
        },
        logout: mockLogout,
      });

      renderLayout();

      expect(screen.queryByText('Organization')).not.toBeInTheDocument();
      expect(screen.getByText('Alpha Team')).toBeInTheDocument();
      expect(screen.getByText('Gamma Team')).toBeInTheDocument();
      expect(screen.getByText('My Dashboard')).toBeInTheDocument();
    });

    it('shows only My Dashboard for MEMBER role', () => {
      mockUseAuth.mockReturnValue({
        user: {
          token: 'test-token',
          userId: 'user-3',
          orgId: 'org-1',
          orgName: 'Acme Corp',
          email: 'member@acme.com',
          displayName: 'Regular Member',
          role: 'MEMBER',
          teams: [
            { teamId: 'team-1', teamName: 'Alpha Team' },
          ],
        },
        logout: mockLogout,
      });

      renderLayout();

      expect(screen.queryByText('Organization')).not.toBeInTheDocument();
      // MEMBER should not see team links (only ORG_ADMIN and TEAM_LEAD get team nav items)
      expect(screen.queryByText('Alpha Team')).not.toBeInTheDocument();
      expect(screen.getByText('My Dashboard')).toBeInTheDocument();
    });
  });

  describe('user info display', () => {
    it('shows the user displayName and role in the sidebar footer', () => {
      mockUseAuth.mockReturnValue({
        user: {
          token: 'test-token',
          userId: 'user-1',
          orgId: 'org-1',
          orgName: 'Acme Corp',
          email: 'admin@acme.com',
          displayName: 'Jane Doe',
          role: 'TEAM_LEAD',
          teams: [{ teamId: 'team-1', teamName: 'Alpha Team' }],
        },
        logout: mockLogout,
      });

      renderLayout();

      expect(screen.getByText('Jane Doe')).toBeInTheDocument();
      expect(screen.getByText('TEAM_LEAD')).toBeInTheDocument();
    });
  });

  describe('logout', () => {
    it('calls logout and navigates to /login when the logout button is clicked', async () => {
      const user = userEvent.setup();

      mockUseAuth.mockReturnValue({
        user: {
          token: 'test-token',
          userId: 'user-1',
          orgId: 'org-1',
          orgName: 'Acme Corp',
          email: 'admin@acme.com',
          displayName: 'Admin User',
          role: 'MEMBER',
          teams: [],
        },
        logout: mockLogout,
      });

      renderLayout();

      const logoutButton = screen.getByText('Logout');
      await user.click(logoutButton);

      expect(mockLogout).toHaveBeenCalledTimes(1);
      expect(mockNavigate).toHaveBeenCalledWith('/login');
    });
  });

  describe('mobile menu', () => {
    it('renders a hamburger button with aria-label "Toggle menu"', () => {
      mockUseAuth.mockReturnValue({
        user: {
          token: 'test-token',
          userId: 'user-1',
          orgId: 'org-1',
          orgName: 'Acme Corp',
          email: 'admin@acme.com',
          displayName: 'Admin User',
          role: 'MEMBER',
          teams: [],
        },
        logout: mockLogout,
      });

      renderLayout();

      const toggleButton = screen.getByLabelText('Toggle menu');
      expect(toggleButton).toBeInTheDocument();
    });
  });
});
