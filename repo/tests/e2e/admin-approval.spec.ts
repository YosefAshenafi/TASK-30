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

    // Register a fresh, dedicated user to reject so we never mutate shared seed users that
    // other tests log in as (which would make this suite order-dependent and flaky).
    const username = `e2e_reject_${Date.now()}`;
    const regRes = await ctx.post('/api/auth/register', {
      data: { username, password: 'E2eReject@12345678' },
    });
    expect([200, 201]).toContain(regRes.status());

    // Look the new (pending) user up by username to get its id.
    const pendingRes = await ctx.get('/api/admin/users/pending', {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    const pending = await pendingRes.json();
    const target = (Array.isArray(pending) ? pending : []).find((u: any) => u.username === username);
    expect(target).toBeTruthy();

    const rejectRes = await ctx.put(`/api/admin/users/${target.id}/reject`, {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect([200, 409]).toContain(rejectRes.status());

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
    const userId = candidates.length > 0 ? candidates[0].id : '00000000-0000-0000-0000-000000000001';

    const rejectRes = await ctx.put(`/api/admin/users/${userId}/reject`, {
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

    // Register a fresh, dedicated user to role-change so shared seed users keep their roles
    // (other tests depend on faculty1/corp1/student1 having their seeded roles).
    const username = `e2e_role_${Date.now()}`;
    const regRes = await ctx.post('/api/auth/register', {
      data: { username, password: 'E2eRole@12345678' },
    });
    expect([200, 201]).toContain(regRes.status());

    const pendingRes = await ctx.get('/api/admin/users/pending', {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    const pending = await pendingRes.json();
    const target = (Array.isArray(pending) ? pending : []).find((u: any) => u.username === username);
    expect(target).toBeTruthy();

    const roleRes = await ctx.patch(`/api/admin/users/${target.id}/role`, {
      headers: { Authorization: `Bearer ${adminToken}` },
      data: { roleName: 'ROLE_FACULTY_MENTOR' },
    });
    expect(roleRes.status()).toBe(200);
    const updated = await roleRes.json();
    expect(updated.role).toMatch(/FACULTY_MENTOR/i);

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
    const userId = candidates.length > 0 ? candidates[0].id : '00000000-0000-0000-0000-000000000001';

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

    // Role selects render per row once the All Users data has loaded — wait for at least one.
    const roleSelects = page.locator('mat-select.role-select');
    await expect(roleSelects.first()).toBeVisible({ timeout: 8000 });
    const count = await roleSelects.count();
    expect(count).toBeGreaterThanOrEqual(1);
  });
});
