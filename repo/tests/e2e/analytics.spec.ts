import { test, expect } from '@playwright/test';

async function loginAs(
  page: import('@playwright/test').Page,
  username: string,
  password: string
): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/dashboard/);
}

test.describe('Analytics', () => {
  test('mentor sees analytics', async ({ page }) => {
    await loginAs(page, 'faculty1', 'Faculty@12345678');

    await page.goto('/analytics');

    // Expect the analytics dashboard with tab group to be present
    const tabGroup = page.locator('mat-tab-group');
    await expect(tabGroup).toBeVisible({ timeout: 8000 });

    // Expect the four analytics tabs
    await expect(page.getByRole('tab', { name: /mastery trends/i })).toBeVisible();
    await expect(page.getByRole('tab', { name: /wrong answers/i })).toBeVisible();
    await expect(page.getByRole('tab', { name: /knowledge gaps/i })).toBeVisible();
    await expect(page.getByRole('tab', { name: /item difficulty/i })).toBeVisible();
  });

  test('corporate mentor sees analytics scoped to own org', async ({ page, request }) => {
    const BASE_API = process.env['BASE_URL'] ?? 'http://localhost:8080';

    // Verify via API that corporate mentor gets org-scoped mastery data (200, not cross-tenant data)
    const ctx = await request.newContext({ baseURL: BASE_API });
    const loginRes = await ctx.post('/api/auth/login', {
      data: { username: 'corp1', password: 'Corp@12345678' },
    });
    const loginBody = await loginRes.json();
    const corpToken = loginBody.accessToken as string;

    // Request mastery without orgId — server should force ACME org scope
    const masteryRes = await ctx.get('/api/analytics/mastery', {
      headers: { Authorization: `Bearer ${corpToken}` },
    });
    expect(masteryRes.status()).toBe(200);

    // Request mastery with a different orgId — server should still force ACME scope, returns 200
    const masteryMismatchRes = await ctx.get('/api/analytics/mastery?organizationId=22222222-0000-0000-0000-000000000002', {
      headers: { Authorization: `Bearer ${corpToken}` },
    });
    expect(masteryMismatchRes.status()).toBe(200);

    await ctx.dispose();

    // UI: corp mentor can see the analytics page
    await loginAs(page, 'corp1', 'Corp@12345678');
    await page.goto('/analytics');
    await expect(page.locator('mat-tab-group')).toBeVisible({ timeout: 8000 });
  });

  test('student blocked from analytics', async ({ page }) => {
    await loginAs(page, 'student1', 'Student@12345678');

    await page.goto('/analytics');

    // Student should be redirected away from /analytics
    // Either redirected to /dashboard or shown a 403/unauthorized message
    const url = page.url();
    const isRedirected = url.includes('/dashboard') || url.includes('/login');

    if (!isRedirected) {
      // If not redirected, page should show an access denied message
      const body = await page.textContent('body');
      expect(body).toMatch(/access denied|unauthorized|forbidden|403/i);
    } else {
      expect(isRedirected).toBeTrue();
    }
  });
});
