import { test, expect } from '@playwright/test';
import { loginAs, TEST_ACCOUNTS } from './fixtures/auth';

test.describe('Team Dashboard (AT-TM-001 to AT-TM-011)', () => {
  test.describe('as TEAM_LEAD', () => {
    test.beforeEach(async ({ page }) => {
      await loginAs(page, TEST_ACCOUNTS.teamLead);
      await page.waitForURL('**/teams/**', { timeout: 15_000 });
      // Wait for team data to load
      await expect(page.getByText('Total Runs')).toBeVisible({ timeout: 15_000 });
    });

    // AT-TM-001: View team KPIs — metric cards visible
    test('AT-TM-001: view team KPIs — metric cards visible', async ({ page }) => {
      await expect(page.getByText('Total Runs')).toBeVisible();
      await expect(page.getByText('Success Rate')).toBeVisible();
      await expect(page.getByText('Failed Runs')).toBeVisible();
      await expect(page.getByText('Total Tokens')).toBeVisible();
      await expect(page.getByText('Total Cost')).toBeVisible();
    });

    // AT-TM-002: User breakdown table visible
    test('AT-TM-002: user breakdown table — Usage by User visible', async ({ page }) => {
      await expect(page.getByText('Usage by User')).toBeVisible({ timeout: 10_000 });
    });

    // AT-TM-003: User table has proper column headers
    test('AT-TM-003: user table has proper column headers', async ({ page }) => {
      await expect(page.getByText('Usage by User')).toBeVisible({ timeout: 10_000 });
      const userSection = page.locator('text=Usage by User').locator('..').locator('..');
      await expect(userSection.locator('th', { hasText: 'User' })).toBeVisible();
      await expect(userSection.locator('th', { hasText: 'Runs' })).toBeVisible();
      await expect(userSection.locator('th', { hasText: 'Tokens' })).toBeVisible();
      await expect(userSection.locator('th', { hasText: 'Cost' })).toBeVisible();
      await expect(userSection.locator('th', { hasText: 'Success Rate' })).toBeVisible();
      await expect(userSection.locator('th', { hasText: 'Avg Duration' })).toBeVisible();
    });

    // AT-TM-004: Chart section visible (Daily Runs)
    test('AT-TM-004: chart section visible — Daily Runs', async ({ page }) => {
      await expect(page.getByText('Daily Runs')).toBeVisible({ timeout: 10_000 });
    });

    // AT-TM-005: Date range selector works
    test('AT-TM-005: date range selector works', async ({ page }) => {
      const last7 = page.getByRole('button', { name: 'Last 7 days' });
      await expect(last7).toBeVisible({ timeout: 10_000 });
      await last7.click();

      // After clicking, data should reload — metric cards should remain visible
      await expect(page.getByText('Total Runs')).toBeVisible({ timeout: 15_000 });
    });

    // AT-TM-006: Refresh button works
    test('AT-TM-006: refresh button works', async ({ page }) => {
      const lastUpdatedLocator = page.getByText('Last updated:');
      await expect(lastUpdatedLocator).toBeVisible({ timeout: 10_000 });

      await page.getByRole('button', { name: 'Refresh' }).click();

      // Verify the page still shows data after refresh
      await expect(page.getByText('Total Runs')).toBeVisible({ timeout: 15_000 });
      await expect(lastUpdatedLocator).toBeVisible();
    });

    // AT-TM-007: Team name in page header
    test('AT-TM-007: team name displayed in header', async ({ page }) => {
      await expect(page.getByRole('heading', { name: /Team:/ })).toBeVisible({ timeout: 10_000 });
    });

    // AT-TM-008: Last updated text present
    test('AT-TM-008: last updated text present', async ({ page }) => {
      await expect(page.getByText('Last updated:')).toBeVisible({ timeout: 10_000 });
    });

    // AT-TM-009: Click user row navigates to /users/:userId
    test('AT-TM-009: click user row navigates to user dashboard', async ({ page }) => {
      await expect(page.getByText('Usage by User')).toBeVisible({ timeout: 10_000 });
      const userSection = page.locator('text=Usage by User').locator('..').locator('..');
      const userRow = userSection.locator('tbody tr').first();
      await expect(userRow).toBeVisible({ timeout: 10_000 });
      await userRow.click();

      await page.waitForURL('**/users/**', { timeout: 10_000 });
      expect(page.url()).toMatch(/\/users\/[a-zA-Z0-9-]+/);
    });
  });

  // AT-TM-010: ORG_ADMIN can view any team
  test('AT-TM-010: ORG_ADMIN can view any team', async ({ page }) => {
    await loginAs(page, TEST_ACCOUNTS.orgAdmin);
    await page.waitForURL('**/org', { timeout: 15_000 });
    await expect(page.getByText('Total Runs')).toBeVisible({ timeout: 15_000 });

    // Navigate to "Usage by Team" section and click a team
    await expect(page.getByText('Usage by Team')).toBeVisible({ timeout: 10_000 });
    const teamSection = page.locator('text=Usage by Team').locator('..').locator('..');
    const teamRow = teamSection.locator('tbody tr').first();
    await teamRow.click();

    // Should navigate to a team dashboard
    await page.waitForURL('**/teams/**', { timeout: 10_000 });
    expect(page.url()).toMatch(/\/teams\/[a-zA-Z0-9-]+/);

    // Team data should load successfully
    await expect(page.getByText('Total Runs')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole('heading', { name: /Team:/ })).toBeVisible({ timeout: 10_000 });
  });

  // AT-TM-011: ORG_ADMIN sees team user rows with navigation arrows
  test('AT-TM-011: ORG_ADMIN sees clickable user rows in team view', async ({ page }) => {
    await loginAs(page, TEST_ACCOUNTS.orgAdmin);
    await page.waitForURL('**/org', { timeout: 15_000 });
    await expect(page.getByText('Usage by Team')).toBeVisible({ timeout: 10_000 });

    // Click into a team
    const teamSection = page.locator('text=Usage by Team').locator('..').locator('..');
    await teamSection.locator('tbody tr').first().click();
    await page.waitForURL('**/teams/**', { timeout: 10_000 });

    // Wait for "Usage by User" section
    await expect(page.getByText('Usage by User')).toBeVisible({ timeout: 15_000 });

    // Click a user row
    const userSection = page.locator('text=Usage by User').locator('..').locator('..');
    const userRow = userSection.locator('tbody tr').first();
    await expect(userRow).toBeVisible({ timeout: 10_000 });
    await userRow.click();

    await page.waitForURL('**/users/**', { timeout: 10_000 });
    expect(page.url()).toMatch(/\/users\/[a-zA-Z0-9-]+/);
  });
});
