import { test, expect } from '@playwright/test';
import { loginAs, TEST_ACCOUNTS, DEFAULT_PASSWORD } from './fixtures/auth';

test.describe('Login Page (AT-LG-001 to AT-LG-008)', () => {
  test.beforeEach(async ({ page }) => {
    // Clear any stored auth state so every test starts logged-out
    await page.goto('/login');
    await page.evaluate(() => {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
    });
    await page.goto('/login');
  });

  // AT-LG-001: Login with valid credentials redirects to dashboard
  test('AT-LG-001: login with valid credentials redirects to dashboard', async ({ page }) => {
    await page.getByLabel('Email').fill(TEST_ACCOUNTS.orgAdmin);
    await page.getByLabel('Password').fill(DEFAULT_PASSWORD);
    await page.getByRole('button', { name: 'Sign In' }).click();

    // ORG_ADMIN should land on /org
    await page.waitForURL('**/org', { timeout: 15_000 });
    expect(page.url()).toContain('/org');
  });

  // AT-LG-002: Login with invalid credentials shows error and stays on /login
  test('AT-LG-002: login with invalid credentials shows error banner', async ({ page }) => {
    await page.getByLabel('Email').fill('nobody@example.com');
    await page.getByLabel('Password').fill('wrongpassword');
    await page.getByRole('button', { name: 'Sign In' }).click();

    // Error message should appear
    await expect(page.getByText('Invalid email or password')).toBeVisible({ timeout: 10_000 });

    // Should still be on /login
    expect(page.url()).toContain('/login');
  });

  // AT-LG-003: Quick-fill test account pre-fills email and password
  test('AT-LG-003: quick-fill test account pre-fills email and password', async ({ page }) => {
    // Click the first test account button (Alice Chen / admin@acme.com)
    await page.getByRole('button', { name: /Alice Chen/ }).click();

    // Verify input values are pre-filled
    await expect(page.getByLabel('Email')).toHaveValue(TEST_ACCOUNTS.orgAdmin);
    await expect(page.getByLabel('Password')).toHaveValue(DEFAULT_PASSWORD);
  });

  // AT-LG-004: ORG_ADMIN redirected to /org
  test('AT-LG-004: ORG_ADMIN redirected to /org', async ({ page }) => {
    await loginAs(page, TEST_ACCOUNTS.orgAdmin);
    await page.waitForURL('**/org', { timeout: 15_000 });
    expect(page.url()).toContain('/org');
  });

  // AT-LG-005: TEAM_LEAD redirected to /teams/:id
  test('AT-LG-005: TEAM_LEAD redirected to /teams/:id', async ({ page }) => {
    await loginAs(page, TEST_ACCOUNTS.teamLead);
    await page.waitForURL('**/teams/**', { timeout: 15_000 });
    expect(page.url()).toMatch(/\/teams\/[a-zA-Z0-9-]+/);
  });

  // AT-LG-006: MEMBER redirected to /me
  test('AT-LG-006: MEMBER redirected to /me', async ({ page }) => {
    await loginAs(page, TEST_ACCOUNTS.member);
    await page.waitForURL('**/me', { timeout: 15_000 });
    expect(page.url()).toContain('/me');
  });

  // AT-LG-007: Logout flow redirects to /login
  test('AT-LG-007: logout redirects to /login', async ({ page }) => {
    await loginAs(page, TEST_ACCOUNTS.orgAdmin);
    await page.waitForURL('**/org', { timeout: 15_000 });

    // Click the Logout button in the sidebar
    await page.getByRole('button', { name: 'Logout' }).click();
    await page.waitForURL('**/login', { timeout: 10_000 });
    expect(page.url()).toContain('/login');
  });

  // AT-LG-008: Multi-org isolation — Acme admin sees Acme data, Globex admin sees Globex data
  test('AT-LG-008: multi-org isolation', async ({ page }) => {
    // Login as Acme admin
    await loginAs(page, TEST_ACCOUNTS.orgAdmin);
    await page.waitForURL('**/org', { timeout: 15_000 });
    await expect(page.getByText('Acme Corp')).toBeVisible({ timeout: 10_000 });

    // Logout
    await page.getByRole('button', { name: 'Logout' }).click();
    await page.waitForURL('**/login', { timeout: 10_000 });

    // Login as Globex admin
    await loginAs(page, TEST_ACCOUNTS.globexAdmin);
    await page.waitForURL('**/org', { timeout: 15_000 });
    await expect(page.getByText('Globex Industries')).toBeVisible({ timeout: 10_000 });

    // Globex admin should NOT see Acme Corp
    await expect(page.getByText('Acme Corp')).not.toBeVisible();
  });
});
