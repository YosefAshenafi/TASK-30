import { test, expect } from './coverage.fixture';
import { API, apiLogin, loginViaUI } from './helpers';

/**
 * Flow 7: Recycle bin restore round-trip — soft-delete then restore a session.
 */
test.describe('Recycle bin restore', () => {
  const studentUser = 'student_e2e_recycle';
  let accessToken: string;
  let sessionId: string;

  test.beforeAll(async ({ request }) => {
    const res = await apiLogin(request, studentUser, 'E2eTest@123!');
    accessToken = res.accessToken;
  });

  test('7a: create a session via API', async ({ request }) => {
    sessionId = `01900000-0000-7000-8000-${Date.now().toString().padStart(12, '0')}`;
    const res = await request.post(`${API}/api/v1/sessions`, {
      headers: { Authorization: `Bearer ${accessToken}` },
      data: {
        id: sessionId,
        courseId: '00000000-0000-0000-0000-000000000010',
        startedAt: new Date().toISOString(),
        restSecondsDefault: 60,
        clientUpdatedAt: new Date().toISOString(),
      },
    });
    expect(res.status()).toBe(201);
  });

  test('7b: soft-delete the session from UI', async ({ page }) => {
    await loginViaUI(page, studentUser, 'E2eTest@123!');
    await page.goto(`/sessions`);

    const sessionRow = page.locator(`[data-session-id="${sessionId}"]`);
    await sessionRow.getByRole('button', { name: /delete/i }).click();
    await page.getByRole('button', { name: /confirm/i }).click();

    await expect(sessionRow).not.toBeVisible();
  });

  test('7c: session appears in recycle bin', async ({ page }) => {
    await loginViaUI(page, studentUser, 'E2eTest@123!');
    await page.goto('/recycle-bin');

    await expect(page.locator(`[data-session-id="${sessionId}"]`)).toBeVisible();
  });

  test('7d: restore session from recycle bin', async ({ page }) => {
    await loginViaUI(page, studentUser, 'E2eTest@123!');
    await page.goto('/recycle-bin');

    const deletedRow = page.locator(`[data-session-id="${sessionId}"]`);
    await deletedRow.getByRole('button', { name: /restore/i }).click();

    await expect(deletedRow).not.toBeVisible();
  });

  test('7e: restored session appears in sessions list', async ({ request }) => {
    const res = await request.get(`${API}/api/v1/sessions/${sessionId}`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.deletedAt).toBeNull();
  });
});
