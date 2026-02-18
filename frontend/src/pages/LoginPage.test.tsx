import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import LoginPage from './LoginPage';

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
function setupAuth(opts: { isAuthenticated?: boolean } = {}) {
  const loginFn = vi.fn();
  mockUseAuth.mockReturnValue({
    user: null,
    isAuthenticated: opts.isAuthenticated ?? false,
    login: loginFn,
    logout: vi.fn(),
  });
  return { loginFn };
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <LoginPage />
    </MemoryRouter>,
  );
}

// ── Tests ───────────────────────────────────────────────────────────────────
describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  // FE-PG-001
  it('renders email/password inputs and Sign In button', () => {
    setupAuth();
    renderPage();

    expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });

  // FE-PG-002
  it('renders 4 test account buttons', () => {
    setupAuth();
    renderPage();

    expect(screen.getByText('admin@acme.com')).toBeInTheDocument();
    expect(screen.getByText('lead-platform@acme.com')).toBeInTheDocument();
    expect(screen.getByText('member1@acme.com')).toBeInTheDocument();
    expect(screen.getByText('admin2@globex.com')).toBeInTheDocument();
  });

  // FE-PG-003
  it('clicking a test account pre-fills email and password fields', async () => {
    setupAuth();
    renderPage();
    const user = userEvent.setup();

    const aliceButton = screen.getByText('Alice Chen').closest('button')!;
    await user.click(aliceButton);

    expect(screen.getByLabelText(/email/i)).toHaveValue('admin@acme.com');
    expect(screen.getByLabelText(/password/i)).toHaveValue('password123');
  });

  // FE-PG-004
  it('submitting valid credentials calls login and navigates to /', async () => {
    const { loginFn } = setupAuth();
    loginFn.mockResolvedValueOnce(undefined);
    renderPage();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText(/email/i), 'admin@acme.com');
    await user.type(screen.getByLabelText(/password/i), 'password123');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(loginFn).toHaveBeenCalledWith('admin@acme.com', 'password123');
    });
    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/');
    });
  });

  // FE-PG-005
  it('submitting invalid credentials shows error message', async () => {
    const { loginFn } = setupAuth();
    loginFn.mockRejectedValueOnce(new Error('bad credentials'));
    renderPage();
    const user = userEvent.setup();

    await user.type(screen.getByLabelText(/email/i), 'bad@example.com');
    await user.type(screen.getByLabelText(/password/i), 'wrong');
    await user.click(screen.getByRole('button', { name: /sign in/i }));

    expect(await screen.findByText('Invalid email or password')).toBeInTheDocument();
  });

  // FE-PG-006
  it('already authenticated user is redirected to /', () => {
    setupAuth({ isAuthenticated: true });
    renderPage();

    expect(mockNavigate).toHaveBeenCalledWith('/');
  });
});
