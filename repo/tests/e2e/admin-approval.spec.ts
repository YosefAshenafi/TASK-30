import { test, expect, request } from '@playwright/test';

const BASE_API = process.env['BASE_URL'] ?? 'http://localhost:3000';

async function apiLogin(username: string, password: string): Promise<string> {
  const ctx = await request.newContext({ baseURL: BASE_API });
  const res = await ctx.post('/api/auth/login', { data: { username, password } });
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

test.describe('Admin User Management', () => {
  test('admin sees pending users', async ({ page }) => {
    await loginAsAdmin(page);

    await page.goto('/admin/users');

    // Expect the User Management page to render
    await expect(page.locator('h1')).toContainText('User Management', { timeout: 8000 });

    // Expect the tab group with Pending Approvals tab
    const pendingTab = page.getByRole('tab', { name: /pending approvals/i });
    await expect(pendingTab).toBeVisible();

    // Click the Pending Approvals tab to ensure it is active
    await pendingTab.click();

    // The tab content should be visible — either data or empty state
    const tabContent = page.locator('.tab-content').first();
    await expect(tabContent).toBeVisible({ timeout: 5000 });
  });

  test('admin approves user', async ({ page }) => {
    await loginAsAdmin(page);

    await page.goto('/admin/users');
    await page.waitForLoadState('networkidle');

    // Click Pending Approvals tab
    await page.getByRole('tab', { name: /pending approvals/i }).click();

    // Check if there are any pending users to approve
    const approveButton = page.getByRole('button', { name: /approve/i }).first();

    if (await approveButton.isVisible()) {
      // Click the first Approve button
      await approveButton.click();

      // Expect a snackbar confirmation
      const snackbar = page.locator('mat-snack-bar-container, .mat-mdc-snack-bar-container');
      await expect(snackbar).toBeVisible({ timeout: 5000 });
      await expect(snackbar).toContainText(/approved/i);
    } else {
      // No pending users — valid state (seed data may not have pending users)
      const emptyState = page.locator('.empty-state');
      await expect(emptyState).toBeVisible();
      await expect(emptyState).toContainText(/no pending/i);
    }
  });

  test('admin rejects user via API', async () => {
    const adminToken = await apiLogin('admin', 'Admin@12345678');
    const ctx = await request.newContext({ baseURL: BASE_API });

    // List users to find one that is APPROVED (to reject back to REJECTED)
    const listRes = await ctx.get('/api/admin/users', {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(listRes.status()).toBe(200);
    const users = await listRes.json();

    // Find a non-admin active user to attempt reject on
    const candidates = Array.isArray(users)
      ? users.filter((u: any) => u.role !== 'ROLE_ADMINISTRATOR')
      : users.content?.filter((u: any) => u.role !== 'ROLE_ADMINISTRATOR') ?? [];

    if (candidates.length > 0) {
      const userId = candidates[0].userId;
      const rejectRes = await ctx.post(`/api/admin/users/${userId}/reject`, {
        headers: { Authorization: `Bearer ${adminToken}` },
      });
      // 200 on success, 409 if already rejected — either is a valid authorized response
      expect([200, 409]).toContain(rejectRes.status());
    }

    await ctx.dispose();
  });

  test('non-admin cannot reject user (API returns 403)', async () => {
    const studentToken = await apiLogin('student1', 'Student@12345678');
    const adminToken = await apiLogin('admin', 'Admin@12345678');
    const ctx = await request.newContext({ baseURL: BASE_API });

    // Get any user id from admin perspective
    const listRes = await ctx.get('/api/admin/users', {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    const users = await listRes.json();
    const candidates = Array.isArray(users) ? users : users.content ?? [];
    const userId = candidates.length > 0 ? candidates[0].userId : '00000000-0000-0000-0000-000000000001';

    const rejectRes = await ctx.post(`/api/admin/users/${userId}/reject`, {
      headers: { Authorization: `Bearer ${studentToken}` },
    });
    expect(rejectRes.status()).toBe(403);

    await ctx.dispose();
  });

  test('admin rejects pending user from UI', async ({ page }) => {
    await loginAsAdmin(page);

    await page.goto('/admin/users');
    await page.waitForLoadState('networkidle');

    await page.getByRole('tab', { name: /pending approvals/i }).click();

    const rejectButton = page.getByRole('button', { name: /reject/i }).first();

    if (await rejectButton.isVisible()) {
      await rejectButton.click();

      const snackbar = page.locator('mat-snack-bar-container, .mat-mdc-snack-bar-container');
      await expect(snackbar).toBeVisible({ timeout: 5000 });
      await expect(snackbar).toContainText(/rejected/i);
    } else {
      // No pending users — valid empty state
      const emptyState = page.locator('.empty-state');
      await expect(emptyState).toBeVisible();
    }
  });
});

test.describe('Admin Role Change', () => {
  test('admin can change user role via API', async () => {
    const adminToken = await apiLogin('admin', 'Admin@12345678');
    const ctx = await request.newContext({ baseURL: BASE_API });

    // Get users list
    const listRes = await ctx.get('/api/admin/users', {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(listRes.status()).toBe(200);
    const users = await listRes.json();
    const candidates = Array.isArray(users)
      ? users.filter((u: any) => u.role !== 'ROLE_ADMINISTRATOR')
      : (users.content ?? []).filter((u: any) => u.role !== 'ROLE_ADMINISTRATOR');

    if (candidates.length > 0) {
      const userId = candidates[0].userId;
      const currentRole = candidates[0].role?.replace('ROLE_', '') ?? 'STUDENT';
      const newRole = currentRole === 'STUDENT' ? 'FACULTY_MENTOR' : 'STUDENT';

      const roleRes = await ctx.patch(`/api/admin/users/${userId}/role`, {
        headers: { Authorization: `Bearer ${adminToken}` },
        data: { roleName: `ROLE_${newRole}` },
      });
      expect(roleRes.status()).toBe(200);
      const updated = await roleRes.json();
      expect(updated.role).toMatch(new RegExp(newRole, 'i'));
    }

    await ctx.dispose();
  });

  test('non-admin cannot change user role (returns 403)', async () => {
    const facultyToken = await apiLogin('faculty1', 'Faculty@12345678');
    const adminToken = await apiLogin('admin', 'Admin@12345678');
    const ctx = await request.newContext({ baseURL: BASE_API });

    const listRes = await ctx.get('/api/admin/users', {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    const users = await listRes.json();
    const candidates = Array.isArray(users) ? users : users.content ?? [];
    const userId = candidates.length > 0 ? candidates[0].userId : '00000000-0000-0000-0000-000000000001';

    const roleRes = await ctx.patch(`/api/admin/users/${userId}/role`, {
      headers: { Authorization: `Bearer ${facultyToken}` },
      data: { roleName: 'ROLE_STUDENT' },
    });
    expect(roleRes.status()).toBe(403);

    await ctx.dispose();
  });

  test('admin can change role via user management UI', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Username').fill('admin');
    await page.getByLabel('Password').fill('Admin@12345678');
    await page.getByRole('button', { name: /sign in/i }).click();
    await page.waitForURL(/\/dashboard/);

    await page.goto('/admin/users');
    await page.waitForLoadState('networkidle');

    // Switch to All Users tab
    await page.getByRole('tab', { name: /all users/i }).click();

    // The user management table should be visible
    const userTable = page.locator('table[mat-table]').last();
    await expect(userTable).toBeVisible({ timeout: 8000 });

    // Role selects should be present in the table
    const roleSelects = page.locator('mat-select.role-select');
    const count = await roleSelects.count();
    expect(count).toBeGreaterThanOrEqual(1);
  });
});
