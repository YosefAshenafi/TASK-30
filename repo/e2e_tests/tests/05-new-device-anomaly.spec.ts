import { test, expect } from './coverage.fixture';
import { API, apiLogin } from './helpers';

/**
 * Flow 5: New-device anomaly — login from unknown fingerprint triggers anomaly notification.
 */
test.describe('New device anomaly detection', () => {
  const knownUser = 'admin';
  let adminToken: string;

  test.beforeAll(async ({ request }) => {
    const res = await apiLogin(request, knownUser, 'Admin@123!');
    adminToken = res.accessToken;
  });

  test('5a: login with a novel device fingerprint succeeds but is flagged', async ({ request }) => {
    const res = await request.post(`${API}/api/v1/auth/login`, {
      data: {
        username: knownUser,
        password: 'Admin@123!',
        deviceFingerprint: 'completely-unknown-device-fp-12345',
      },
    });
    // Login succeeds — anomaly is logged, not blocked
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.accessToken).toBeTruthy();
  });

  test('5b: anomaly event is recorded in the audit log', async ({ request }) => {
    const res = await request.get(`${API}/api/v1/admin/audit-logs`, {
      headers: { Authorization: `Bearer ${adminToken}` },
      params: { eventType: 'NEW_DEVICE_LOGIN', limit: 5 },
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.content.length).toBeGreaterThan(0);
  });

  test('5c: admin notification for anomaly appears in inbox', async ({ request }) => {
    const res = await request.get(`${API}/api/v1/notifications`, {
      headers: { Authorization: `Bearer ${adminToken}` },
      params: { unreadOnly: true },
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    const anomalyNotif = body.content.find(
      (n: { type: string }) => n.type === 'NEW_DEVICE_LOGIN' || n.type === 'SECURITY_ANOMALY'
    );
    expect(anomalyNotif).toBeTruthy();
  });
});
