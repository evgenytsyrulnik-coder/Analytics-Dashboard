import { test, expect } from '@playwright/test';
import { loginAs, TEST_ACCOUNTS } from './fixtures/auth';

test.describe('Run Detail (AT-RN-001 to AT-RN-009)', () => {
  /**
   * Helper: logs in as ORG_ADMIN and navigates to a run detail page
   * by going through the runs list and clicking the first run row.
   */
  async function navigateToRunDetail(page: import('@playwright/test').Page) {
    await loginAs(page, TEST_ACCOUNTS.orgAdmin);
    await page.waitForURL('**/org', { timeout: 15_000 });

    // Go to runs list
    await page.goto('/org/runs');
    await expect(page.locator('tbody tr').first()).toBeVisible({ timeout: 15_000 });

    // Click first run
    await page.locator('tbody tr').first().click();
    await page.waitForURL('**/runs/**', { timeout: 10_000 });

    // Wait for run detail to load
    await expect(page.getByText('Run Metadata')).toBeVisible({ timeout: 15_000 });
  }

  // AT-RN-001: View run metadata — Run ID visible
  test('AT-RN-001: run metadata — Run ID visible', async ({ page }) => {
    await navigateToRunDetail(page);
    await expect(page.getByText('Run ID')).toBeVisible();
  });

  // AT-RN-002: Agent Type visible
  test('AT-RN-002: run metadata — Agent Type visible', async ({ page }) => {
    await navigateToRunDetail(page);
    await expect(page.getByText('Agent Type')).toBeVisible();
  });

  // AT-RN-003: Model visible
  test('AT-RN-003: run metadata — Model visible', async ({ page }) => {
    await navigateToRunDetail(page);
    await expect(page.getByText('Model')).toBeVisible();
  });

  // AT-RN-004: Status visible
  test('AT-RN-004: run metadata — Status visible', async ({ page }) => {
    await navigateToRunDetail(page);
    await expect(page.getByText('Status')).toBeVisible();
  });

  // AT-RN-005: Started and Finished timestamps visible
  test('AT-RN-005: run metadata — Started and Finished visible', async ({ page }) => {
    await navigateToRunDetail(page);
    await expect(page.getByText('Started')).toBeVisible();
    await expect(page.getByText('Finished')).toBeVisible();
  });

  // AT-RN-006: Duration visible
  test('AT-RN-006: run metadata — Duration visible', async ({ page }) => {
    await navigateToRunDetail(page);
    await expect(page.getByText('Duration')).toBeVisible();
  });

  // AT-RN-007: Token breakdown visible
  test('AT-RN-007: token breakdown — Input Tokens, Output Tokens, Total Tokens visible', async ({ page }) => {
    await navigateToRunDetail(page);
    await expect(page.getByText('Token & Cost Breakdown')).toBeVisible();
    await expect(page.getByText('Input Tokens')).toBeVisible();
    await expect(page.getByText('Output Tokens')).toBeVisible();
    await expect(page.getByText('Total Tokens')).toBeVisible();
  });

  // AT-RN-008: Cost breakdown visible
  test('AT-RN-008: cost breakdown — Input Cost, Output Cost, Total Cost visible', async ({ page }) => {
    await navigateToRunDetail(page);
    await expect(page.getByText('Input Cost')).toBeVisible();
    await expect(page.getByText('Output Cost')).toBeVisible();
    await expect(page.getByText('Total Cost')).toBeVisible();
  });

  // AT-RN-009: Back button present and works
  test('AT-RN-009: back button present and works', async ({ page }) => {
    await navigateToRunDetail(page);
    const backButton = page.getByRole('button', { name: /Back/ });
    await expect(backButton).toBeVisible({ timeout: 10_000 });

    // Click Back
    await backButton.click();

    // Should navigate back (to runs list or previous page)
    await page.waitForURL((url) => !url.pathname.match(/\/runs\/[a-zA-Z0-9-]+$/), { timeout: 10_000 });
  });
});
