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

test.describe('Sessions', () => {
  test('student can start session', async ({ page }) => {
    await loginAs(page, 'student1', 'Student@12345678');

    await page.goto('/sessions/new');

    // The session capture UI should render: timer, activities card, action buttons
    await expect(page.locator('[data-testid="elapsed-timer"]')).toBeVisible({ timeout: 8000 });
  });

  test('offline banner shows when network intercepted', async ({ page }) => {
    await loginAs(page, 'student1', 'Student@12345678');

    // Load a session and then cut network to simulate offline
    await page.goto('/sessions');
    await page.waitForLoadState('networkidle');

    // Intercept all API requests to simulate going offline
    await page.route('**/api/**', (route) => route.abort('internetdisconnected'));

    // Trigger an action that would go to session capture
    // The offline banner should appear when the component detects offline status
    await page.evaluate(() => {
      window.dispatchEvent(new Event('offline'));
    });

    // Navigate to sessions/new — API calls will fail (offline mode)
    await page.goto('/sessions/new');

    // Give time for the offline banner to render
    const offlineBanner = page.locator('.offline-banner');
    await expect(offlineBanner).toBeVisible({ timeout: 5000 });
  });
});
