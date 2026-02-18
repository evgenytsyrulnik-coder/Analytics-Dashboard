import { type Page, expect } from '@playwright/test';

/**
 * Test accounts seeded by the backend DataSeeder.
 * All accounts share the password "password123".
 */
export const TEST_ACCOUNTS = {
  orgAdmin: 'admin@acme.com',
  teamLead: 'lead-platform@acme.com',
  member: 'member1@acme.com',
  globexAdmin: 'admin2@globex.com',
} as const;

export const DEFAULT_PASSWORD = 'password123';

/**
 * Logs in as the given user through the UI login form.
 *
 * 1. Navigates to /login
 * 2. Fills in email and password
 * 3. Clicks Sign In
 * 4. Waits for the URL to change away from /login
 */
export async function loginAs(page: Page, email: string, password: string = DEFAULT_PASSWORD): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign In' }).click();
  // Wait until we navigate away from /login (role-based redirect kicks in)
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 15_000 });
}
