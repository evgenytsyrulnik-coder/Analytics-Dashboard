import React, { type ReactElement } from 'react';
import { render, type RenderOptions } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, type MemoryRouterProps } from 'react-router-dom';
import { AuthProvider } from '@/context/AuthContext';
import type { LoginResponse } from '@/types';
import { buildLoginResponse } from './factories';

// ── Wrapper that provides Router + Auth ─────────────────────────────────────

interface WrapperOptions {
  /** Initial entries for MemoryRouter (defaults to ['/']) */
  routerProps?: MemoryRouterProps;
}

function createWrapper({ routerProps }: WrapperOptions = {}) {
  return function Wrapper({ children }: { children: React.ReactNode }) {
    return (
      <MemoryRouter {...routerProps}>
        <AuthProvider>{children}</AuthProvider>
      </MemoryRouter>
    );
  };
}

// ── Custom render ───────────────────────────────────────────────────────────

interface CustomRenderOptions extends Omit<RenderOptions, 'wrapper'> {
  routerProps?: MemoryRouterProps;
}

/**
 * Renders a component wrapped in MemoryRouter + AuthProvider.
 * Returns all @testing-library/react utilities plus a pre-configured
 * `user` instance from @testing-library/user-event.
 */
export function renderWithProviders(
  ui: ReactElement,
  options: CustomRenderOptions = {},
) {
  const { routerProps, ...renderOptions } = options;
  const user = userEvent.setup();

  return {
    user,
    ...render(ui, {
      wrapper: createWrapper({ routerProps }),
      ...renderOptions,
    }),
  };
}

// ── Render as authenticated user ────────────────────────────────────────────

interface AuthenticatedRenderOptions extends CustomRenderOptions {
  /** Override the default authenticated user. Defaults to an ORG_ADMIN. */
  userOverrides?: Partial<LoginResponse>;
}

/**
 * Renders a component as an authenticated user by pre-seeding localStorage
 * with a token and user data before the AuthProvider mounts.
 *
 * The AuthProvider reads `localStorage.getItem('user')` on mount, so seeding
 * it beforehand makes the context start in an authenticated state.
 */
export function renderAuthenticated(
  ui: ReactElement,
  options: AuthenticatedRenderOptions = {},
) {
  const { userOverrides, ...rest } = options;
  const loginResponse = buildLoginResponse(userOverrides);

  // Seed localStorage before AuthProvider initialises
  localStorage.setItem('token', loginResponse.token);
  localStorage.setItem('user', JSON.stringify(loginResponse));

  return {
    /** The LoginResponse that was injected into localStorage */
    authUser: loginResponse,
    ...renderWithProviders(ui, rest),
  };
}

// ── Re-exports for convenience ──────────────────────────────────────────────
export { screen, waitFor, within, act } from '@testing-library/react';
export { default as userEvent } from '@testing-library/user-event';
export { server } from './mocks/server';
export { http, HttpResponse } from 'msw';
