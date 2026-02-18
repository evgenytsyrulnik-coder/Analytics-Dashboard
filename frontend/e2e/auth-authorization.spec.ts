import { test, expect } from '@playwright/test';
import { loginAs, TEST_ACCOUNTS, DEFAULT_PASSWORD } from './fixtures/auth';

test.describe('Auth & Authorization (AT-AU-001 to AT-AU-009)', () => {
  test.beforeEach(async ({ page }) => {
    // Ensure clean slate — clear localStorage
    await page.goto('/login');
    await page.evaluate(() => {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
    });
  });

  // AT-AU-001: Login via email/password stores JWT
  test('AT-AU-001: login stores JWT in localStorage', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill(TEST_ACCOUNTS.orgAdmin);
    await page.getByLabel('Password').fill(DEFAULT_PASSWORD);
    await page.getByRole('button', { name: 'Sign In' }).click();
    await page.waitForURL('**/org', { timeout: 15_000 });

    // Verify JWT is stored in localStorage
    const token = await page.evaluate(() => localStorage.getItem('token'));
    expect(token).toBeTruthy();
    expect(typeof token).toBe('string');
    expect(token!.length).toBeGreaterThan(10);
  });

  // AT-AU-002: Login stores user object in localStorage
  test('AT-AU-002: login stores user object in localStorage', async ({ page }) => {
    await loginAs(page, TEST_ACCOUNTS.orgAdmin);

    const userJson = await page.evaluate(() => localStorage.getItem('user'));
    expect(userJson).toBeTruthy();

    const user = JSON.parse(userJson!);
    expect(user.email).toBe(TEST_ACCOUNTS.orgAdmin);
    expect(user.role).toBe('ORG_ADMIN');
    expect(user.token).toBeTruthy();
  });

  // AT-AU-003: Role-based navigation — ORG_ADMIN defaults to /org
  test('AT-AU-003: ORG_ADMIN default redirect to /org', async ({ page }) => {
    await loginAs(page, TEST_ACCOUNTS.orgAdmin);
    await page.waitForURL('**/org', { timeout: 15_000 });
    expect(page.url()).toContain('/org');
  });

  // AT-AU-004: Role-based navigation — TEAM_LEAD defaults to /teams/:id
  test('AT-AU-004: TEAM_LEAD default redirect to /teams/:id', async ({ page }) => {
    await loginAs(page, TEST_ACCOUNTS.teamLead);
    await page.waitForURL('**/teams/**', { timeout: 15_000 });
    expect(page.url()).toMatch(/\/teams\/[a-zA-Z0-9-]+/);
  });

  // AT-AU-005: Role-based navigation — MEMBER defaults to /me
  test('AT-AU-005: MEMBER default redirect to /me', async ({ page }) => {
    await loginAs(page, TEST_ACCOUNTS.member);
    await page.waitForURL('**/me', { timeout: 15_000 });
    expect(page.url()).toContain('/me');
  });

  // AT-AU-006: ORG_ADMIN full access — can visit org, team, user, run pages
  test('AT-AU-006: ORG_ADMIN full access to all pages', async ({ page }) => {
    await loginAs(page, TEST_ACCOUNTS.orgAdmin);
    await page.waitForURL('**/org', { timeout: 15_000 });

    // Can access /org
    await expect(page.getByText('Total Runs')).toBeVisible({ timeout: 15_000 });

    // Can access /org/runs
    await page.goto('/org/runs');
    await expect(page.getByRole('heading', { name: 'Runs' })).toBeVisible({ timeout: 15_000 });

    // Navigate to a team via sidebar
    const teamLink = page.locator('nav a[href*="/teams/"]').first();
    const teamLinkVisible = await teamLink.isVisible().catch(() => false);
    if (teamLinkVisible) {
      await teamLink.click();
      await page.waitForURL('**/teams/**', { timeout: 10_000 });
      await expect(page.getByText('Total Runs')).toBeVisible({ timeout: 15_000 });
    }

    // Can access /me
    await page.goto('/me');
    await expect(page.getByRole('heading', { name: 'My Analytics' })).toBeVisible({ timeout: 15_000 });
  });

  // AT-AU-007: TEAM_LEAD scoped access — can see own team and personal
  test('AT-AU-007: TEAM_LEAD scoped access — own team and personal', async ({ page }) => {
    await loginAs(page, TEST_ACCOUNTS.teamLead);
    await page.waitForURL('**/teams/**', { timeout: 15_000 });

    // Can see team dashboard
    await expect(page.getByText('Total Runs')).toBeVisible({ timeout: 15_000 });

    // Can access /me
    await page.goto('/me');
    await expect(page.getByRole('heading', { name: 'My Analytics' })).toBeVisible({ timeout: 15_000 });
  });

  // AT-AU-008: MEMBER restricted access — personal dashboard only
  test('AT-AU-008: MEMBER restricted access — personal dashboard', async ({ page }) => {
    await loginAs(page, TEST_ACCOUNTS.member);
    await page.waitForURL('**/me', { timeout: 15_000 });

    // Can see personal dashboard
    await expect(page.getByText('My Runs')).toBeVisible({ timeout: 15_000 });

    // Trying to access /users/:id as a MEMBER shows permission error
    await page.goto('/users/some-user-id');
    await expect(page.getByText(/permission/i)).toBeVisible({ timeout: 10_000 });
  });

  // AT-AU-009: Unauthenticated access redirects to /login
  test('AT-AU-009: unauthenticated access redirects to /login', async ({ page }) => {
    // Ensure no tokens
    await page.goto('/login');
    await page.evaluate(() => {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
    });

    // Try to access protected route directly
    await page.goto('/org');
    await page.waitForURL('**/login', { timeout: 10_000 });
    expect(page.url()).toContain('/login');

    // Try another protected route
    await page.goto('/me');
    await page.waitForURL('**/login', { timeout: 10_000 });
    expect(page.url()).toContain('/login');
  });
});
