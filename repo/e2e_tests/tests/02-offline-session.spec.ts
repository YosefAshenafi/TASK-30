import { test, expect, BrowserContext } from './coverage.fixture';
import { API, apiLogin, loginViaUI } from './helpers';

/**
 * Flow 2: Student goes offline, completes 3 sets, reconnects, server state matches.
 */
test.describe('Offline session sync', () => {
  const studentUser = 'student_e2e_offline';
  let accessToken: string;
  let sessionId: string;

  test.beforeAll(async ({ request }) => {
    const res = await apiLogin(request, studentUser, 'E2eTest@123!');
    accessToken = res.accessToken;
  });

  test('2a: student logs in, starts session, then goes offline', async ({ page, context }) => {
    await loginViaUI(page, studentUser, 'E2eTest@123!');
    await expect(page).toHaveURL(/home/);

    await page.getByRole('link', { name: /sessions/i }).click();
    await page.getByRole('button', { name: /new session/i }).click();
    await page.getByLabel(/course/i).selectOption({ index: 1 });
    await page.getByRole('button', { name: /start/i }).click();

    await expect(page).toHaveURL(/sessions\/.+\/run/);
    sessionId = page.url().split('/sessions/')[1].split('/run')[0];

    // Simulate offline
    await context.setOffline(true);
    await expect(page.getByText(/offline/i)).toBeVisible();
  });

  test('2b: log 3 sets while offline', async ({ page, context }) => {
    // Re-navigate into offline state
    await context.setOffline(true);

    for (let i = 1; i <= 3; i++) {
      await page.getByRole('button', { name: /log set/i }).click();
      await page.getByLabel(/reps/i).fill('10');
      await page.getByLabel(/weight/i).fill(String(50 + i * 5));
      await page.getByRole('button', { name: /save/i }).click();
      await expect(page.getByText(new RegExp(`set ${i}`, 'i'))).toBeVisible();
    }
  });

  test('2c: reconnect and verify outbox drains', async ({ page, context }) => {
    await context.setOffline(false);
    await expect(page.getByText(/offline/i)).not.toBeVisible({ timeout: 10_000 });

    // Wait for background sync to flush outbox
    await page.waitForTimeout(3_000);
  });

  test('2d: server has 3 sets for the session', async ({ request }) => {
    const res = await request.get(`${API}/api/v1/sessions/${sessionId}`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(res.status()).toBe(200);
    const session = await res.json();
    expect(session.sets).toHaveLength(3);
  });
});
