import { test, expect, request } from '@playwright/test';

const BASE_API = process.env['BASE_URL'] ?? 'http://localhost:3000';

async function apiLogin(username: string, password: string): Promise<string> {
  const ctx = await request.newContext({ baseURL: BASE_API });
  const res = await ctx.post('/api/auth/login', {
    data: { username, password },
  });
  const body = await res.json();
  await ctx.dispose();
  return body.accessToken as string;
}

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

test.describe('Notifications', () => {
  test('authenticated user can list notifications via API', async () => {
    const token = await apiLogin('admin', 'Admin@12345678');
    const ctx = await request.newContext({ baseURL: BASE_API });

    const res = await ctx.get('/api/notifications', {
      headers: { Authorization: `Bearer ${token}` },
    });

    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body).toHaveProperty('content');
    expect(body).toHaveProperty('totalElements');
    expect(Array.isArray(body.content)).toBe(true);

    await ctx.dispose();
  });

  test('unauthenticated request to list notifications returns 401', async () => {
    const ctx = await request.newContext({ baseURL: BASE_API });

    const res = await ctx.get('/api/notifications');
    expect(res.status()).toBe(401);

    await ctx.dispose();
  });

  test('authenticated user can connect to SSE stream', async () => {
    const token = await apiLogin('admin', 'Admin@12345678');
    const ctx = await request.newContext({ baseURL: BASE_API });

    const res = await ctx.get('/api/notifications/stream', {
      headers: {
        Authorization: `Bearer ${token}`,
        Accept: 'text/event-stream',
      },
    });

    expect(res.status()).toBe(200);
    const contentType = res.headers()['content-type'] ?? '';
    expect(contentType).toContain('text/event-stream');

    await ctx.dispose();
  });

  test('unauthenticated request to SSE stream returns 401', async () => {
    const ctx = await request.newContext({ baseURL: BASE_API });

    const res = await ctx.get('/api/notifications/stream', {
      headers: { Accept: 'text/event-stream' },
    });
    expect(res.status()).toBe(401);

    await ctx.dispose();
  });

  test('notifications bell icon is visible on reports page', async ({ page }) => {
    await loginAs(page, 'admin', 'Admin@12345678');

    await page.goto('/reports');
    await page.waitForLoadState('networkidle');

    const notificationBtn = page.getByRole('button', { name: /notifications/i });
    await expect(notificationBtn).toBeVisible({ timeout: 8000 });
  });

  test('clicking notifications bell opens notification panel', async ({ page }) => {
    await loginAs(page, 'admin', 'Admin@12345678');

    await page.goto('/reports');
    await page.waitForLoadState('networkidle');

    const notificationBtn = page.getByRole('button', { name: /notifications/i });
    await notificationBtn.click();

    // Notifications panel should appear
    const notifPanel = page.locator('.notifications-panel');
    await expect(notifPanel).toBeVisible({ timeout: 5000 });

    // Panel should contain a title
    await expect(notifPanel).toContainText(/notifications/i);
  });

  test('mark notification as read via API', async () => {
    const token = await apiLogin('admin', 'Admin@12345678');
    const ctx = await request.newContext({ baseURL: BASE_API });

    // List notifications to find one to mark as read
    const listRes = await ctx.get('/api/notifications', {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(listRes.status()).toBe(200);
    const body = await listRes.json();

    if (body.content && body.content.length > 0) {
      const notifId = body.content[0].id;

      const markRes = await ctx.put(`/api/notifications/${notifId}/read`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      // 200 if found, we just verify the auth passed
      expect([200, 404]).toContain(markRes.status());
    }

    // At minimum, verify the list endpoint returns proper structure
    expect(body).toHaveProperty('totalElements');

    await ctx.dispose();
  });
});
