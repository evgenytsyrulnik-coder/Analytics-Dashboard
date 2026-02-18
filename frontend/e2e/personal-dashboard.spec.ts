import { test, expect } from '@playwright/test';
import { loginAs, TEST_ACCOUNTS } from './fixtures/auth';

test.describe('Personal Dashboard (AT-US-001 to AT-US-010)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, TEST_ACCOUNTS.member);
    await page.waitForURL('**/me', { timeout: 15_000 });
    // Wait for personal data to load
    await expect(page.getByText('My Runs')).toBeVisible({ timeout: 15_000 });
  });

  // AT-US-001: Metric cards visible — My Runs, Success Rate, Total Tokens, Total Cost
  test('AT-US-001: metric cards visible', async ({ page }) => {
    await expect(page.getByText('My Runs')).toBeVisible();
    await expect(page.getByText('Success Rate')).toBeVisible();
    await expect(page.getByText('Total Tokens')).toBeVisible();
    await expect(page.getByText('Total Cost')).toBeVisible();
  });

  // AT-US-002: Team rank badge visible
  test('AT-US-002: team rank badge visible', async ({ page }) => {
    // The badge text says "You are #N of M engineers..."
    await expect(page.getByText(/You are #\d+ of \d+ engineers/)).toBeVisible({ timeout: 10_000 });
  });

  // AT-US-003: Chart section — "My Daily Usage"
  test('AT-US-003: chart section — My Daily Usage visible', async ({ page }) => {
    await expect(page.getByText('My Daily Usage')).toBeVisible({ timeout: 10_000 });
  });

  // AT-US-004: Recent Runs table visible
  test('AT-US-004: recent runs table visible', async ({ page }) => {
    await expect(page.getByText('Recent Runs')).toBeVisible({ timeout: 10_000 });
  });

  // AT-US-005: Recent Runs table has proper column headers
  test('AT-US-005: recent runs table has proper column headers', async ({ page }) => {
    await expect(page.getByText('Recent Runs')).toBeVisible({ timeout: 10_000 });
    const runsSection = page.locator('text=Recent Runs').locator('..').locator('..');
    await expect(runsSection.locator('th', { hasText: 'Time' })).toBeVisible();
    await expect(runsSection.locator('th', { hasText: 'Agent Type' })).toBeVisible();
    await expect(runsSection.locator('th', { hasText: 'Status' })).toBeVisible();
    await expect(runsSection.locator('th', { hasText: 'Duration' })).toBeVisible();
    await expect(runsSection.locator('th', { hasText: 'Tokens' })).toBeVisible();
    await expect(runsSection.locator('th', { hasText: 'Cost' })).toBeVisible();
  });

  // AT-US-006: Click run row navigates to /runs/:runId
  test('AT-US-006: click run row navigates to run detail', async ({ page }) => {
    await expect(page.getByText('Recent Runs')).toBeVisible({ timeout: 10_000 });
    const runsSection = page.locator('text=Recent Runs').locator('..').locator('..');
    const runRow = runsSection.locator('tbody tr').first();
    await expect(runRow).toBeVisible({ timeout: 10_000 });
    await runRow.click();

    await page.waitForURL('**/runs/**', { timeout: 10_000 });
    expect(page.url()).toMatch(/\/runs\/[a-zA-Z0-9-]+/);
  });

  // AT-US-007: Date range selector works
  test('AT-US-007: date range selector works', async ({ page }) => {
    const last7 = page.getByRole('button', { name: 'Last 7 days' });
    await expect(last7).toBeVisible({ timeout: 10_000 });
    await last7.click();

    // After clicking, data should reload — metric cards should remain visible
    await expect(page.getByText('My Runs')).toBeVisible({ timeout: 15_000 });
  });

  // AT-US-008: Refresh button works
  test('AT-US-008: refresh button works', async ({ page }) => {
    const lastUpdatedLocator = page.getByText('Last updated:');
    await expect(lastUpdatedLocator).toBeVisible({ timeout: 10_000 });

    await page.getByRole('button', { name: 'Refresh' }).click();

    await expect(page.getByText('My Runs')).toBeVisible({ timeout: 15_000 });
    await expect(lastUpdatedLocator).toBeVisible();
  });

  // AT-US-009: Page heading says "My Analytics"
  test('AT-US-009: page heading says My Analytics', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'My Analytics' })).toBeVisible({ timeout: 10_000 });
  });

  // AT-US-010: Last updated text present
  test('AT-US-010: last updated text present', async ({ page }) => {
    await expect(page.getByText('Last updated:')).toBeVisible({ timeout: 10_000 });
  });
});
