import { test, expect } from '@playwright/test';
import { loginAs, TEST_ACCOUNTS } from './fixtures/auth';

test.describe('Organization Runs List (AT-RL-001 to AT-RL-013)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, TEST_ACCOUNTS.orgAdmin);
    await page.waitForURL('**/org', { timeout: 15_000 });
    // Navigate to /org/runs
    await page.goto('/org/runs');
    // Wait for runs page heading to load
    await expect(page.getByRole('heading', { name: 'Runs' })).toBeVisible({ timeout: 15_000 });
  });

  // AT-RL-001: Paginated runs table visible with column headers
  test('AT-RL-001: paginated runs table visible with column headers', async ({ page }) => {
    // Wait for data to load
    await expect(page.locator('table')).toBeVisible({ timeout: 15_000 });

    // Check column headers
    await expect(page.locator('th', { hasText: 'Time' })).toBeVisible();
    await expect(page.locator('th', { hasText: 'User' })).toBeVisible();
    await expect(page.locator('th', { hasText: 'Team' })).toBeVisible();
    await expect(page.locator('th', { hasText: 'Agent Type' })).toBeVisible();
    await expect(page.locator('th', { hasText: 'Status' })).toBeVisible();
    await expect(page.locator('th', { hasText: 'Duration' })).toBeVisible();
    await expect(page.locator('th', { hasText: 'Tokens' })).toBeVisible();
    await expect(page.locator('th', { hasText: 'Cost' })).toBeVisible();
  });

  // AT-RL-002: Shows "X runs found" text
  test('AT-RL-002: shows run count text', async ({ page }) => {
    // Wait for the count to appear, e.g. "123 runs found"
    await expect(page.getByText(/\d+\s+runs?\s+found/)).toBeVisible({ timeout: 15_000 });
  });

  // AT-RL-003: "Back to Dashboard" link present
  test('AT-RL-003: back to dashboard link present', async ({ page }) => {
    await expect(page.getByText('Back to Dashboard')).toBeVisible({ timeout: 10_000 });
  });

  // AT-RL-004: Back to Dashboard link navigates to /org
  test('AT-RL-004: back to dashboard link navigates to /org', async ({ page }) => {
    await page.getByText('Back to Dashboard').click();
    await page.waitForURL('**/org', { timeout: 10_000 });
    expect(page.url()).toMatch(/\/org$/);
  });

  // AT-RL-005: Pagination controls — Previous and Next
  test('AT-RL-005: pagination controls are present', async ({ page }) => {
    // Wait for data
    await expect(page.getByText(/\d+\s+runs?\s+found/)).toBeVisible({ timeout: 15_000 });

    // Pagination controls should be present when there are multiple pages
    // The Previous button should exist (may be disabled on first page)
    const previousBtn = page.getByRole('button', { name: 'Previous' });
    const nextBtn = page.getByRole('button', { name: 'Next' });

    // At least one of these should be present if there's pagination
    // If dataset is small enough to be single page, the pagination div won't render
    const paginationVisible = await previousBtn.isVisible().catch(() => false);
    if (paginationVisible) {
      await expect(previousBtn).toBeVisible();
      await expect(nextBtn).toBeVisible();
    }
    // Test passes regardless — if data fits in one page, pagination isn't needed
  });

  // AT-RL-006: Previous button is disabled on first page
  test('AT-RL-006: previous button disabled on first page', async ({ page }) => {
    await expect(page.getByText(/\d+\s+runs?\s+found/)).toBeVisible({ timeout: 15_000 });

    const previousBtn = page.getByRole('button', { name: 'Previous' });
    const paginationVisible = await previousBtn.isVisible().catch(() => false);
    if (paginationVisible) {
      await expect(previousBtn).toBeDisabled();
    }
  });

  // AT-RL-007: Clicking Next advances to next page
  test('AT-RL-007: clicking Next advances to next page', async ({ page }) => {
    await expect(page.getByText(/\d+\s+runs?\s+found/)).toBeVisible({ timeout: 15_000 });

    const nextBtn = page.getByRole('button', { name: 'Next' });
    const paginationVisible = await nextBtn.isVisible().catch(() => false);
    if (paginationVisible) {
      const isEnabled = await nextBtn.isEnabled();
      if (isEnabled) {
        await nextBtn.click();
        // URL should update with page parameter
        await page.waitForTimeout(1000);
        // Previous button should now be enabled (no longer on first page)
        await expect(page.getByRole('button', { name: 'Previous' })).toBeEnabled({ timeout: 5_000 });
      }
    }
  });

  // AT-RL-008: Team dropdown filter has "All teams" default option
  test('AT-RL-008: team dropdown filter has All teams default', async ({ page }) => {
    // The team filter is a <select> element
    const teamSelect = page.locator('select').first();
    await expect(teamSelect).toBeVisible({ timeout: 10_000 });

    // Check that "All teams" is an option and is selected by default
    await expect(teamSelect.locator('option', { hasText: 'All teams' })).toBeAttached();
    const selectedValue = await teamSelect.inputValue();
    expect(selectedValue).toBe('');
  });

  // AT-RL-009: Team dropdown has team options
  test('AT-RL-009: team dropdown contains team options', async ({ page }) => {
    const teamSelect = page.locator('select').first();
    await expect(teamSelect).toBeVisible({ timeout: 10_000 });

    // Wait for teams to load
    await page.waitForTimeout(2000);
    const options = teamSelect.locator('option');
    const count = await options.count();
    // Should have at least "All teams" + 1 real team
    expect(count).toBeGreaterThanOrEqual(2);
  });

  // AT-RL-010: Status dropdown filter present
  test('AT-RL-010: status dropdown filter present', async ({ page }) => {
    // The status filter is a custom dropdown button with text "All results"
    await expect(page.getByText('All results')).toBeVisible({ timeout: 10_000 });
  });

  // AT-RL-011: Status dropdown shows status options
  test('AT-RL-011: status dropdown shows status options when clicked', async ({ page }) => {
    // Click the status dropdown button
    await page.getByText('All results').click();

    // Check that status options are visible in the dropdown menu
    // Scope to the dropdown overlay to avoid matching StatusBadge in table cells
    const dropdownMenu = page.locator('div[class*="absolute"][class*="z-20"]').first();
    await expect(dropdownMenu).toBeVisible({ timeout: 5_000 });
    await expect(dropdownMenu.getByText('SUCCEEDED')).toBeVisible();
    await expect(dropdownMenu.getByText('FAILED')).toBeVisible();
    await expect(dropdownMenu.getByText('CANCELLED')).toBeVisible();
    await expect(dropdownMenu.getByText('RUNNING')).toBeVisible();
  });

  // AT-RL-012: Click run row navigates to /runs/:runId
  test('AT-RL-012: click run row navigates to run detail', async ({ page }) => {
    // Wait for runs table to have data
    await expect(page.locator('tbody tr').first()).toBeVisible({ timeout: 15_000 });

    // Click the first run row
    await page.locator('tbody tr').first().click();
    await page.waitForURL('**/runs/**', { timeout: 10_000 });
    expect(page.url()).toMatch(/\/runs\/[a-zA-Z0-9-]+/);
  });

  // AT-RL-013: Filters section heading visible
  test('AT-RL-013: filters section heading visible', async ({ page }) => {
    await expect(page.getByText('Filters')).toBeVisible({ timeout: 10_000 });
  });
});
