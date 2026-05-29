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

    // Open the capture screen while still online so the component mounts and subscribes to the
    // SyncService offline stream (offlineMode$ is a live window-event stream with no replay, so
    // the subscription must exist before the offline event fires).
    await page.goto('/sessions/new');
    await page.waitForLoadState('domcontentloaded');

    // Now go offline. Playwright fires the window 'offline' event the component listens for,
    // which flips isOffline and reveals the banner.
    await page.context().setOffline(true);

    const offlineBanner = page.locator('.offline-banner');
    await expect(offlineBanner).toBeVisible({ timeout: 8000 });

    await page.context().setOffline(false);
  });
});
