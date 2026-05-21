import { test, expect, request } from '@playwright/test';

const BASE_API = process.env['BASE_URL'] ?? 'http://localhost:8080';

async function apiLogin(username: string, password: string) {
  const ctx = await request.newContext({ baseURL: BASE_API });
  const res = await ctx.post('/api/auth/login', {
    data: { username, password },
  });
  const body = await res.json();
  await ctx.dispose();
  return body.accessToken as string;
}

test.describe('Export approval workflow', () => {
  test('export without approval is blocked for non-admin', async () => {
    const token = await apiLogin('faculty1', 'Faculty@12345678');
    const ctx = await request.newContext({ baseURL: BASE_API });

    const res = await ctx.post('/api/reports/export', {
      headers: { Authorization: `Bearer ${token}` },
      data: { reportType: 'ENROLLMENTS', format: 'CSV' },
    });

    expect(res.status()).toBe(400);
    const body = await res.json();
    expect(body.message).toMatch(/approval/i);
    await ctx.dispose();
  });

  test('admin can export without approval', async () => {
    const token = await apiLogin('admin', 'Admin@12345678');
    const ctx = await request.newContext({ baseURL: BASE_API });

    const res = await ctx.post('/api/reports/export', {
      headers: { Authorization: `Bearer ${token}` },
      data: { reportType: 'ENROLLMENTS', format: 'CSV' },
    });

    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body).toHaveProperty('filename');
    await ctx.dispose();
  });

  test('non-admin can export with approved approval', async () => {
    const facultyToken = await apiLogin('faculty1', 'Faculty@12345678');
    const adminToken = await apiLogin('admin', 'Admin@12345678');

    const ctx = await request.newContext({ baseURL: BASE_API });

    // Create approval request
    const createRes = await ctx.post('/api/approvals', {
      headers: { Authorization: `Bearer ${facultyToken}` },
      data: { type: 'EXPORT', entityType: 'REPORT', entityId: 'ENROLLMENTS' },
    });
    expect(createRes.status()).toBe(201);
    const approval = await createRes.json();
    const approvalId = approval.id;

    // Admin approves it
    const approveRes = await ctx.put(`/api/approvals/${approvalId}/approve`, {
      headers: { Authorization: `Bearer ${adminToken}` },
    });
    expect(approveRes.status()).toBe(200);

    // Faculty now exports with the approved approvalId
    const exportRes = await ctx.post('/api/reports/export', {
      headers: { Authorization: `Bearer ${facultyToken}` },
      data: { reportType: 'ENROLLMENTS', format: 'CSV', approvalId },
    });
    expect(exportRes.status()).toBe(200);
    const exportBody = await exportRes.json();
    expect(exportBody).toHaveProperty('filename');

    await ctx.dispose();
  });
});
