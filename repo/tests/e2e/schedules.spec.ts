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

async function loginAsAdmin(page: import('@playwright/test').Page): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('Username').fill('admin');
  await page.getByLabel('Password').fill('Admin@12345678');
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/dashboard/);
}

test.describe('Report Schedule CRUD', () => {
  test('admin can create a report schedule via API', async () => {
    const token = await apiLogin('admin', 'Admin@12345678');
    const ctx = await request.newContext({ baseURL: BASE_API });

    const createRes = await ctx.post('/api/reports/schedules', {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        reportType: 'ENROLLMENTS',
        cronExpression: '0 0 6 * * ?',
        outputFormat: 'CSV',
        outputPath: '/var/reports/enrollments.csv',
      },
    });

    expect(createRes.status()).toBe(201);
    const schedule = await createRes.json();
    expect(schedule).toHaveProperty('id');
    expect(schedule.reportType).toBe('ENROLLMENTS');

    await ctx.dispose();
  });

  test('admin can list report schedules via API', async () => {
    const token = await apiLogin('admin', 'Admin@12345678');
    const ctx = await request.newContext({ baseURL: BASE_API });

    // Create one to ensure at least one exists
    await ctx.post('/api/reports/schedules', {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        reportType: 'SEAT_UTILIZATION',
        cronExpression: '0 0 8 * * ?',
        outputFormat: 'PDF',
        outputPath: '/var/reports/seat-utilization.pdf',
      },
    });

    const listRes = await ctx.get('/api/reports/schedules', {
      headers: { Authorization: `Bearer ${token}` },
    });

    expect(listRes.status()).toBe(200);
    const body = await listRes.json();
    expect(body).toHaveProperty('content');
    expect(Array.isArray(body.content)).toBe(true);

    await ctx.dispose();
  });

  test('admin can delete a report schedule via API', async () => {
    const token = await apiLogin('admin', 'Admin@12345678');
    const ctx = await request.newContext({ baseURL: BASE_API });

    // Create a schedule to delete
    const createRes = await ctx.post('/api/reports/schedules', {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        reportType: 'REFUNDS',
        cronExpression: '0 0 7 * * ?',
        outputFormat: 'CSV',
        outputPath: '/var/reports/refunds.csv',
      },
    });
    expect(createRes.status()).toBe(201);
    const schedule = await createRes.json();

    // Delete it
    const deleteRes = await ctx.delete(`/api/reports/schedules/${schedule.id}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(deleteRes.status()).toBe(204);

    await ctx.dispose();
  });

  test('student cannot create a report schedule', async () => {
    const token = await apiLogin('student1', 'Student@12345678');
    const ctx = await request.newContext({ baseURL: BASE_API });

    const res = await ctx.post('/api/reports/schedules', {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        reportType: 'ENROLLMENTS',
        cronExpression: '0 0 6 * * ?',
        outputFormat: 'CSV',
        outputPath: '/var/reports/enrollments.csv',
      },
    });

    expect(res.status()).toBe(403);
    await ctx.dispose();
  });

  test('unauthenticated request to list schedules returns 401', async () => {
    const ctx = await request.newContext({ baseURL: BASE_API });

    const res = await ctx.get('/api/reports/schedules');
    expect(res.status()).toBe(401);

    await ctx.dispose();
  });

  test('admin can open schedule dialog from reports UI', async ({ page }) => {
    await loginAsAdmin(page);

    await page.goto('/reports');
    await page.waitForLoadState('networkidle');

    // Click Schedule button in the Enrollments tab
    const scheduleBtn = page.getByRole('button', { name: /schedule/i }).first();
    await expect(scheduleBtn).toBeVisible({ timeout: 8000 });
    await scheduleBtn.click();

    // Dialog should open
    const dialog = page.locator('mat-dialog-container, [role="dialog"]');
    await expect(dialog).toBeVisible({ timeout: 5000 });
    await expect(dialog).toContainText(/schedule report/i);

    // Close dialog
    const cancelBtn = dialog.getByRole('button', { name: /cancel/i });
    await cancelBtn.click();
    await expect(dialog).not.toBeVisible({ timeout: 3000 });
  });
});
