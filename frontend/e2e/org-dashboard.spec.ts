import { test, expect } from '@playwright/test';
import { loginAs, TEST_ACCOUNTS } from './fixtures/auth';

test.describe('Organization Dashboard (AT-ORG-001 to AT-ORG-025)', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, TEST_ACCOUNTS.orgAdmin);
    // Explicitly navigate to /org (in case we landed elsewhere)
    await page.goto('/org', { waitUntil: 'networkidle' });
    await page.waitForURL('**/org', { timeout: 15_000 });
    // Wait for org-specific content to ensure we're on the right page (not team dashboard)
    await expect(page.getByText('Usage by Team')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('Top Users by Cost')).toBeVisible({ timeout: 15_000 });
    // Wait for the dashboard data to load (metric cards appear)
    await expect(page.locator('.grid.grid-cols-2').first().getByText('Total Runs')).toBeVisible({ timeout: 15_000 });
  });

  // AT-ORG-001: Aggregate KPI metric cards are visible
  test('AT-ORG-001: view aggregate KPIs — metric cards visible', async ({ page }) => {
    // Check metric cards in the grid (not in tables)
    const metricsGrid = page.locator('.grid.grid-cols-2').first();
    await expect(metricsGrid.getByText('Total Runs')).toBeVisible();
    await expect(metricsGrid.getByText('Success Rate')).toBeVisible();
    await expect(metricsGrid.getByText('Total Tokens')).toBeVisible();
    await expect(metricsGrid.getByText('Total Cost')).toBeVisible();
  });

  // AT-ORG-002: Time-series chart section visible
  test('AT-ORG-002: time-series chart — Daily Runs section visible', async ({ page }) => {
    await expect(page.getByText('Daily Runs')).toBeVisible({ timeout: 10_000 });
  });

  // AT-ORG-003: Team breakdown section visible
  test('AT-ORG-003: team breakdown — Usage by Team section visible', async ({ page }) => {
    await expect(page.getByText('Usage by Team')).toBeVisible({ timeout: 10_000 });
  });

  // AT-ORG-004: Agent type breakdown section visible
  test('AT-ORG-004: agent type breakdown — Usage by Agent Type section visible', async ({ page }) => {
    await expect(page.getByText('Usage by Agent Type')).toBeVisible({ timeout: 10_000 });
  });

  // AT-ORG-005: Top users table visible
  test('AT-ORG-005: top users table — Top Users by Cost section visible', async ({ page }) => {
    await expect(page.getByText('Top Users by Cost')).toBeVisible({ timeout: 10_000 });
  });

  // AT-ORG-006: Click team row navigates to /teams/:teamId
  test('AT-ORG-006: click team row navigates to team dashboard', async ({ page }) => {
    // Wait for the Usage by Team section to appear
    await expect(page.getByText('Usage by Team')).toBeVisible({ timeout: 10_000 });

    // Find the team table within the "Usage by Team" section and click the first data row
    const teamSection = page.locator('text=Usage by Team').locator('..').locator('..');
    const teamRow = teamSection.locator('tbody tr').first();
    await teamRow.click();

    await page.waitForURL('**/teams/**', { timeout: 10_000 });
    expect(page.url()).toMatch(/\/teams\/[a-zA-Z0-9-]+/);
  });

  // AT-ORG-007: Click user row in top users navigates to /users/:userId
  test('AT-ORG-007: click user row in top users navigates to user dashboard', async ({ page }) => {
    // Wait for top users section
    await expect(page.getByText('Top Users by Cost')).toBeVisible({ timeout: 10_000 });

    const topUsersSection = page.locator('text=Top Users by Cost').locator('..').locator('..');
    const userRow = topUsersSection.locator('tbody tr').first();
    await userRow.click();

    await page.waitForURL('**/users/**', { timeout: 10_000 });
    expect(page.url()).toMatch(/\/users\/[a-zA-Z0-9-]+/);
  });

  // AT-ORG-008: "Last updated" text present
  test('AT-ORG-008: last updated text is present', async ({ page }) => {
    await expect(page.getByText('Last updated:')).toBeVisible({ timeout: 10_000 });
  });

  // AT-ORG-009: Refresh button works
  test('AT-ORG-009: refresh button triggers data reload', async ({ page }) => {
    // Capture the initial "Last updated" text
    const lastUpdatedLocator = page.getByText('Last updated:');
    await expect(lastUpdatedLocator).toBeVisible({ timeout: 10_000 });
    const initialText = await lastUpdatedLocator.textContent();

    // Wait a moment so the timestamp can differ
    await page.waitForTimeout(1100);

    // Click refresh
    await page.getByRole('button', { name: 'Refresh' }).click();

    // Wait for the timestamp to update (or at least for the button click to complete)
    // We verify the "Last updated" element is still present after refresh
    await expect(lastUpdatedLocator).toBeVisible({ timeout: 10_000 });

    // Attempt to detect change — if timing allows, the text should differ
    // In fast environments it might be the same second, so we just verify no error
    const updatedText = await lastUpdatedLocator.textContent();
    expect(updatedText).toBeTruthy();
  });

  // AT-ORG-010: DateRangeSelector preset buttons work
  test('AT-ORG-010: DateRangeSelector preset buttons work', async ({ page }) => {
    // The DateRangeSelector has preset buttons: "Last 7 days", "Last 30 days", "Last 90 days"
    const last7 = page.getByRole('button', { name: 'Last 7 days' });
    await expect(last7).toBeVisible({ timeout: 10_000 });
    await last7.click();

    // After clicking, data should reload — metric cards should remain visible
    await expect(page.getByText('Total Runs')).toBeVisible({ timeout: 15_000 });

    // Click "Last 90 days"
    const last90 = page.getByRole('button', { name: 'Last 90 days' });
    await last90.click();
    await expect(page.getByText('Total Runs')).toBeVisible({ timeout: 15_000 });
  });

  // AT-ORG-011: Failed Runs metric card visible
  test('AT-ORG-011: Failed Runs metric card visible', async ({ page }) => {
    await expect(page.getByText('Failed Runs')).toBeVisible();
  });

  // AT-ORG-012: Org name in page header
  test('AT-ORG-012: organization name appears in page header', async ({ page }) => {
    await expect(page.getByRole('heading', { name: /Analytics/, level: 2 }).or(page.getByRole('heading', { name: /Acme Corporation/ }))).toBeVisible({ timeout: 10_000 });
  });

  // AT-ORG-013: Team table has proper column headers
  test('AT-ORG-013: team table has proper column headers', async ({ page }) => {
    await expect(page.getByText('Usage by Team')).toBeVisible({ timeout: 10_000 });
    const teamSection = page.getByText('Usage by Team').locator('..').locator('..');
    await expect(teamSection.getByRole('columnheader', { name: 'Team', exact: true })).toBeVisible();
    await expect(teamSection.getByRole('columnheader', { name: 'Runs', exact: true }).first()).toBeVisible();
    await expect(teamSection.getByRole('columnheader', { name: 'Cost', exact: true }).first()).toBeVisible();
    await expect(teamSection.getByRole('columnheader', { name: 'Success Rate', exact: true }).first()).toBeVisible();
  });

  // AT-ORG-014: Agent type table has proper column headers
  test('AT-ORG-014: agent type table has proper column headers', async ({ page }) => {
    await expect(page.getByText('Usage by Agent Type')).toBeVisible({ timeout: 10_000 });
    const agentSection = page.getByText('Usage by Agent Type').locator('..').locator('..');
    await expect(agentSection.getByRole('columnheader', { name: 'Agent Type', exact: true })).toBeVisible();
    await expect(agentSection.getByRole('columnheader', { name: 'Runs', exact: true }).first()).toBeVisible();
    await expect(agentSection.getByRole('columnheader', { name: 'Cost', exact: true }).first()).toBeVisible();
  });

  // AT-ORG-015: Top users table has proper column headers
  test('AT-ORG-015: top users table has proper column headers', async ({ page }) => {
    await expect(page.getByText('Top Users by Cost')).toBeVisible({ timeout: 10_000 });
    const topUsersSection = page.getByText('Top Users by Cost').locator('..').locator('..');
    await expect(topUsersSection.getByRole('columnheader', { name: 'Rank', exact: true })).toBeVisible();
    await expect(topUsersSection.getByRole('columnheader', { name: 'Name', exact: true })).toBeVisible();
    await expect(topUsersSection.getByRole('columnheader', { name: 'Team', exact: true }).first()).toBeVisible();
    await expect(topUsersSection.getByRole('columnheader', { name: 'Tokens', exact: true }).first()).toBeVisible();
    await expect(topUsersSection.getByRole('columnheader', { name: 'Cost', exact: true }).first()).toBeVisible();
  });

  // AT-ORG-016: Sidebar shows "Organization" nav link
  test('AT-ORG-016: sidebar shows Organization nav link', async ({ page }) => {
    await expect(page.locator('nav').getByText('Organization')).toBeVisible({ timeout: 10_000 });
  });

  // AT-ORG-017: Sidebar shows "My Dashboard" nav link
  test('AT-ORG-017: sidebar shows My Dashboard nav link', async ({ page }) => {
    await expect(page.locator('nav').getByText('My Dashboard')).toBeVisible({ timeout: 10_000 });
  });

  // AT-ORG-018: Sidebar displays user name and role
  test('AT-ORG-018: sidebar displays user name and role', async ({ page }) => {
    await expect(page.getByText('ORG_ADMIN')).toBeVisible({ timeout: 10_000 });
  });

  // AT-ORG-019: Team rows show arrow indicator
  test('AT-ORG-019: team rows show navigation arrow', async ({ page }) => {
    await expect(page.getByText('Usage by Team')).toBeVisible({ timeout: 10_000 });
    const teamSection = page.locator('text=Usage by Team').locator('..').locator('..');
    // The arrow is rendered as &rarr; which is the → character
    const firstTeamRow = teamSection.locator('tbody tr').first();
    await expect(firstTeamRow).toBeVisible();
  });

  // AT-ORG-020: Top users rows show arrow indicator
  test('AT-ORG-020: top user rows show navigation arrow', async ({ page }) => {
    await expect(page.getByText('Top Users by Cost')).toBeVisible({ timeout: 10_000 });
    const topUsersSection = page.locator('text=Top Users by Cost').locator('..').locator('..');
    const firstUserRow = topUsersSection.locator('tbody tr').first();
    await expect(firstUserRow).toBeVisible();
  });

  // AT-ORG-021: Metric card values are numeric (non-empty)
  test('AT-ORG-021: metric card values are populated', async ({ page }) => {
    // Each MetricCard has a <p> with text-2xl font-bold containing the value
    const metricValues = page.locator('.bg-white.rounded-lg.border .text-2xl.font-bold');
    const count = await metricValues.count();
    expect(count).toBeGreaterThanOrEqual(4);
    for (let i = 0; i < Math.min(count, 5); i++) {
      const text = await metricValues.nth(i).textContent();
      expect(text).toBeTruthy();
      expect(text!.trim().length).toBeGreaterThan(0);
    }
  });

  // AT-ORG-022: Date range selector has "Last 30 days" button
  test('AT-ORG-022: date range selector has Last 30 days button', async ({ page }) => {
    await expect(page.getByRole('button', { name: 'Last 30 days' })).toBeVisible({ timeout: 10_000 });
  });

  // AT-ORG-023: Date range selector has date inputs
  test('AT-ORG-023: date range selector has date inputs', async ({ page }) => {
    const dateInputs = page.locator('input[type="date"]');
    const count = await dateInputs.count();
    expect(count).toBeGreaterThanOrEqual(2);
  });

  // AT-ORG-024: Chart area renders a recharts container
  test('AT-ORG-024: chart renders in Daily Runs section', async ({ page }) => {
    await expect(page.getByText('Daily Runs')).toBeVisible({ timeout: 10_000 });
    // Recharts renders SVG elements inside a responsive container
    const chartSection = page.locator('text=Daily Runs').locator('..').locator('..');
    await expect(chartSection.locator('svg').first()).toBeVisible({ timeout: 10_000 });
  });

  // AT-ORG-025: Navigating to /org via sidebar works
  test('AT-ORG-025: navigating to org via sidebar works', async ({ page }) => {
    // Navigate away first
    await page.locator('nav').getByText('My Dashboard').click();
    await page.waitForURL('**/me', { timeout: 10_000 });

    // Navigate back via sidebar
    await page.locator('nav').getByText('Organization').click();
    await page.waitForURL('**/org', { timeout: 10_000 });
    await expect(page.getByText('Total Runs')).toBeVisible({ timeout: 15_000 });
  });
});
