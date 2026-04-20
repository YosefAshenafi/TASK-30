import { test, expect } from './coverage.fixture';
import { API, apiLogin, loginViaUI } from './helpers';

/**
 * Flow 4: Admin triggers large export → export.ready notification appears.
 */
test.describe('Export notification', () => {
  let adminToken: string;

  test.beforeAll(async ({ request }) => {
    const res = await apiLogin(request, 'admin', 'Admin@123!');
    adminToken = res.accessToken;
  });

  test('4a: admin triggers a data export', async ({ request }) => {
    const res = await request.post(`${API}/api/v1/admin/exports`, {
      headers: { Authorization: `Bearer ${adminToken}` },
      data: { type: 'SESSIONS', format: 'CSV', filters: {} },
    });
    expect([200, 202]).toContain(res.status());
  });

  test('4b: notification appears in admin inbox', async ({ page }) => {
    await loginViaUI(page, 'admin', 'Admin@123!');
    await expect(page).toHaveURL(/home/);

    // Poll the notification bell for up to 30 seconds for the export.ready notification
    await expect(async () => {
      await page.getByRole('link', { name: /notifications|inbox/i }).click();
      await expect(page.getByText(/export.*ready|export completed/i)).toBeVisible();
    }).toPass({ timeout: 30_000 });
  });

  test('4c: notification can be marked as read', async ({ page }) => {
    await loginViaUI(page, 'admin', 'Admin@123!');
    await page.getByRole('link', { name: /notifications|inbox/i }).click();

    const notification = page.getByText(/export.*ready|export completed/i).first();
    await notification.click();
    await expect(notification).not.toHaveClass(/unread/);
  });
});
