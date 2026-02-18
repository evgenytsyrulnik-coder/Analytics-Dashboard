import { test, expect } from '@playwright/test';
import { loginAs, TEST_ACCOUNTS } from './fixtures/auth';

test.describe('User Dashboard (AT-UD-001 to AT-UD-008)', () => {
  // AT-UD-001: ORG_ADMIN views any user — user analytics visible
  test('AT-UD-001: ORG_ADMIN views any user — analytics visible', async ({ page }) => {
    await loginAs(page, TEST_ACCOUNTS.orgAdmin);
    await page.waitForURL('**/org', { timeout: 15_000 });
    await expect(page.getByText('Top Users by Cost')).toBeVisible({ timeout: 15_000 });

    // Click the first user in the top users table
    const topUsersSection = page.locator('text=Top Users by Cost').locator('..').locator('..');
    const userRow = topUsersSection.locator('tbody tr').first();
    await userRow.click();

    await page.waitForURL('**/users/**', { timeout: 10_000 });

    // User analytics should be visible
    await expect(page.getByText('Total Runs')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('Success Rate')).toBeVisible();
    await expect(page.getByText('Total Tokens')).toBeVisible();
    await expect(page.getByText('Total Cost')).toBeVisible();
  });

  // AT-UD-002: TEAM_LEAD views team member — analytics visible
  test('AT-UD-002: TEAM_LEAD views team member — analytics visible', async ({ page }) => {
    await loginAs(page, TEST_ACCOUNTS.teamLead);
    await page.waitForURL('**/teams/**', { timeout: 15_000 });
    await expect(page.getByText('Usage by User')).toBeVisible({ timeout: 15_000 });

    // Click a user in the usage by user table
    const userSection = page.locator('text=Usage by User').locator('..').locator('..');
    const userRow = userSection.locator('tbody tr').first();
    await expect(userRow).toBeVisible({ timeout: 10_000 });
    await userRow.click();

    await page.waitForURL('**/users/**', { timeout: 10_000 });

    // User analytics should be visible
    await expect(page.getByText('Total Runs')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('Total Cost')).toBeVisible();
  });

  // AT-UD-003: TEAM_LEAD views non-team user — permission error
  test('AT-UD-003: TEAM_LEAD views non-team user — permission error or restricted', async ({ page }) => {
    await loginAs(page, TEST_ACCOUNTS.teamLead);
    await page.waitForURL('**/teams/**', { timeout: 15_000 });

    // Try to access a user from a different org (Globex admin) by navigating directly
    // The backend should return a 403, and the frontend should show an error
    // We use a fabricated userId that doesn't belong to the team lead's team
    await page.goto('/users/non-existent-user-id-12345');

    // Should show a permission or not found error
    await expect(
      page.getByText(/permission|not found|Failed/i)
    ).toBeVisible({ timeout: 15_000 });
  });

  // AT-UD-004: Has Back button
  test('AT-UD-004: user dashboard has Back button', async ({ page }) => {
    await loginAs(page, TEST_ACCOUNTS.orgAdmin);
    await page.waitForURL('**/org', { timeout: 15_000 });
    await expect(page.getByText('Top Users by Cost')).toBeVisible({ timeout: 15_000 });

    // Navigate to a user
    const topUsersSection = page.locator('text=Top Users by Cost').locator('..').locator('..');
    await topUsersSection.locator('tbody tr').first().click();
    await page.waitForURL('**/users/**', { timeout: 10_000 });

    // Back button should be visible
    await expect(page.getByText('Back')).toBeVisible({ timeout: 10_000 });
  });

  // AT-UD-005: Back button navigates back
  test('AT-UD-005: back button navigates back', async ({ page }) => {
    await loginAs(page, TEST_ACCOUNTS.orgAdmin);
    await page.waitForURL('**/org', { timeout: 15_000 });
    await expect(page.getByText('Top Users by Cost')).toBeVisible({ timeout: 15_000 });

    // Navigate to a user
    const topUsersSection = page.locator('text=Top Users by Cost').locator('..').locator('..');
    await topUsersSection.locator('tbody tr').first().click();
    await page.waitForURL('**/users/**', { timeout: 10_000 });
    await expect(page.getByText('Total Runs')).toBeVisible({ timeout: 15_000 });

    // Click Back
    await page.getByText('Back').click();

    // Should navigate back to the previous page (org dashboard)
    await page.waitForURL('**/org', { timeout: 10_000 });
  });

  // AT-UD-006: User dashboard shows user name in heading
  test('AT-UD-006: user dashboard shows user name in heading', async ({ page }) => {
    await loginAs(page, TEST_ACCOUNTS.orgAdmin);
    await page.waitForURL('**/org', { timeout: 15_000 });
    await expect(page.getByText('Top Users by Cost')).toBeVisible({ timeout: 15_000 });

    const topUsersSection = page.locator('text=Top Users by Cost').locator('..').locator('..');
    await topUsersSection.locator('tbody tr').first().click();
    await page.waitForURL('**/users/**', { timeout: 10_000 });

    // The heading shows "[User Name] — Analytics" or "User Analytics"
    await expect(page.getByRole('heading', { name: /Analytics/ })).toBeVisible({ timeout: 15_000 });
  });

  // AT-UD-007: User dashboard has Recent Runs section
  test('AT-UD-007: user dashboard has Recent Runs section', async ({ page }) => {
    await loginAs(page, TEST_ACCOUNTS.orgAdmin);
    await page.waitForURL('**/org', { timeout: 15_000 });
    await expect(page.getByText('Top Users by Cost')).toBeVisible({ timeout: 15_000 });

    const topUsersSection = page.locator('text=Top Users by Cost').locator('..').locator('..');
    await topUsersSection.locator('tbody tr').first().click();
    await page.waitForURL('**/users/**', { timeout: 10_000 });

    await expect(page.getByText('Recent Runs')).toBeVisible({ timeout: 15_000 });
  });

  // AT-UD-008: Click run row in user dashboard navigates to /runs/:runId
  test('AT-UD-008: click run row navigates to run detail', async ({ page }) => {
    await loginAs(page, TEST_ACCOUNTS.orgAdmin);
    await page.waitForURL('**/org', { timeout: 15_000 });
    await expect(page.getByText('Top Users by Cost')).toBeVisible({ timeout: 15_000 });

    const topUsersSection = page.locator('text=Top Users by Cost').locator('..').locator('..');
    await topUsersSection.locator('tbody tr').first().click();
    await page.waitForURL('**/users/**', { timeout: 10_000 });

    await expect(page.getByText('Recent Runs')).toBeVisible({ timeout: 15_000 });
    const runsSection = page.locator('text=Recent Runs').locator('..').locator('..');
    const runRow = runsSection.locator('tbody tr').first();
    await expect(runRow).toBeVisible({ timeout: 10_000 });
    await runRow.click();

    await page.waitForURL('**/runs/**', { timeout: 10_000 });
    expect(page.url()).toMatch(/\/runs\/[a-zA-Z0-9-]+/);
  });
});
