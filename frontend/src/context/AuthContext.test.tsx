import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AuthProvider, useAuth } from '@/context/AuthContext';
import { server } from '@/test/mocks/server';
import { http, HttpResponse } from 'msw';

// ── Test component that exposes AuthContext values ──────────────────────────
function TestComponent() {
  const { user, isAuthenticated, login, logout } = useAuth();
  return (
    <div>
      <span data-testid="auth-status">{isAuthenticated ? 'true' : 'false'}</span>
      <span data-testid="user-name">{user?.displayName ?? 'none'}</span>
      <span data-testid="user-role">{user?.role ?? 'none'}</span>
      <span data-testid="user-token">{user?.token ?? 'none'}</span>
      <button onClick={() => login('admin@acme.com', 'password123')}>Login</button>
      <button onClick={logout}>Logout</button>
    </div>
  );
}

// ── Helper: render with AuthProvider ────────────────────────────────────────
function renderWithAuth() {
  const user = userEvent.setup();
  const result = render(
    <AuthProvider>
      <TestComponent />
    </AuthProvider>,
  );
  return { ...result, user };
}

// ── Test Suite ──────────────────────────────────────────────────────────────
describe('AuthContext', () => {
  // FE-AC-001: login() with valid credentials stores token and sets isAuthenticated
  it('FE-AC-001: login() with valid credentials stores token in localStorage and sets isAuthenticated=true', async () => {
    const { user } = renderWithAuth();

    // Initially not authenticated
    expect(screen.getByTestId('auth-status')).toHaveTextContent('false');
    expect(screen.getByTestId('user-name')).toHaveTextContent('none');

    // Click Login button (triggers login with valid credentials)
    await user.click(screen.getByRole('button', { name: 'Login' }));

    // After login, isAuthenticated should be true
    await waitFor(() => {
      expect(screen.getByTestId('auth-status')).toHaveTextContent('true');
    });

    expect(screen.getByTestId('user-name')).toHaveTextContent('Alice Admin');

    // Verify localStorage has the token and user data
    expect(localStorage.getItem('token')).toBe('test-jwt-token-abc123');
    const storedUser = JSON.parse(localStorage.getItem('user')!);
    expect(storedUser.email).toBe('alice.admin@acme.com');
    expect(storedUser.role).toBe('ORG_ADMIN');
  });

  // FE-AC-002: login() with invalid credentials throws, isAuthenticated remains false
  it('FE-AC-002: login() with invalid credentials throws error and isAuthenticated remains false', async () => {
    // Override the handler to reject credentials
    server.use(
      http.post('/api/v1/auth/login', () =>
        HttpResponse.json({ detail: 'Invalid credentials' }, { status: 401 }),
      ),
    );

    // We need a component that catches the error from login()
    function TestComponentWithError() {
      const { isAuthenticated, login } = useAuth();
      const [error, setError] = React.useState<string | null>(null);
      return (
        <div>
          <span data-testid="auth-status">{isAuthenticated ? 'true' : 'false'}</span>
          <span data-testid="error-message">{error ?? 'none'}</span>
          <button
            onClick={async () => {
              try {
                await login('bad@example.com', 'wrongpassword');
              } catch (e: unknown) {
                setError('login_failed');
              }
            }}
          >
            Login
          </button>
        </div>
      );
    }

    const testUser = userEvent.setup();
    render(
      <AuthProvider>
        <TestComponentWithError />
      </AuthProvider>,
    );

    expect(screen.getByTestId('auth-status')).toHaveTextContent('false');

    await testUser.click(screen.getByRole('button', { name: 'Login' }));

    await waitFor(() => {
      expect(screen.getByTestId('error-message')).toHaveTextContent('login_failed');
    });

    // isAuthenticated should still be false
    expect(screen.getByTestId('auth-status')).toHaveTextContent('false');

    // localStorage should NOT have a token
    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('user')).toBeNull();
  });

  // FE-AC-003: logout() clears localStorage and sets user to null
  it('FE-AC-003: logout() clears localStorage, user=null, isAuthenticated=false', async () => {
    const { user } = renderWithAuth();

    // First log in
    await user.click(screen.getByRole('button', { name: 'Login' }));

    await waitFor(() => {
      expect(screen.getByTestId('auth-status')).toHaveTextContent('true');
    });

    // Verify token is stored before logout
    expect(localStorage.getItem('token')).toBe('test-jwt-token-abc123');

    // Now log out
    await user.click(screen.getByRole('button', { name: 'Logout' }));

    expect(screen.getByTestId('auth-status')).toHaveTextContent('false');
    expect(screen.getByTestId('user-name')).toHaveTextContent('none');

    // localStorage should be cleared
    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('user')).toBeNull();
  });

  // FE-AC-004: Page reload with stored user → restores user from localStorage
  it('FE-AC-004: restores user from localStorage on mount (simulating page reload)', () => {
    // Pre-populate localStorage with a stored user
    const storedUser = {
      token: 'persisted-token',
      userId: '00000000-0000-0000-0000-000000000002',
      orgId: '00000000-0000-0000-0000-000000000001',
      orgName: 'Acme Corp',
      email: 'alice.admin@acme.com',
      displayName: 'Restored User',
      role: 'ORG_ADMIN',
      teams: [{ teamId: 'team-1', teamName: 'Platform Team' }],
    };
    localStorage.setItem('user', JSON.stringify(storedUser));
    localStorage.setItem('token', 'persisted-token');

    renderWithAuth();

    // Should be authenticated immediately on mount
    expect(screen.getByTestId('auth-status')).toHaveTextContent('true');
    expect(screen.getByTestId('user-name')).toHaveTextContent('Restored User');
  });

  // FE-AC-005: useAuth outside AuthProvider throws an error
  it('FE-AC-005: useAuth outside AuthProvider throws an error', () => {
    // Suppress React error boundary console output during this test
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    expect(() => {
      render(<TestComponent />);
    }).toThrow('useAuth must be used within AuthProvider');

    consoleSpy.mockRestore();
  });

  // FE-AC-006: login stores complete user data in localStorage
  it('FE-AC-006: login() stores complete user data including teams and orgId', async () => {
    const { user } = renderWithAuth();

    await user.click(screen.getByRole('button', { name: 'Login' }));

    await waitFor(() => {
      expect(screen.getByTestId('auth-status')).toHaveTextContent('true');
    });

    // Verify the full user object is stored in localStorage
    const storedUser = JSON.parse(localStorage.getItem('user')!);
    expect(storedUser.token).toBe('test-jwt-token-abc123');
    expect(storedUser.userId).toBe('00000000-0000-0000-0000-000000000002');
    expect(storedUser.orgId).toBe('00000000-0000-0000-0000-000000000001');
    expect(storedUser.orgName).toBe('Acme Corp');
    expect(storedUser.displayName).toBe('Alice Admin');
    expect(storedUser.role).toBe('ORG_ADMIN');
    expect(storedUser.teams).toHaveLength(2);
    expect(storedUser.teams[0].teamId).toBe('00000000-0000-0000-0000-000000000010');
  });
});
