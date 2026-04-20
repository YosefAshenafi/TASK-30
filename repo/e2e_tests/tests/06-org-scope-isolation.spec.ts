import { test, expect } from './coverage.fixture';
import { API, apiLogin } from './helpers';

/**
 * Flow 6: Corporate Mentor scope isolation — Org-A mentor cannot see Org-B data.
 */
test.describe('Organisation scope isolation', () => {
  let mentorAToken: string;
  let mentorBToken: string;
  let orgAStudentId: string;
  let orgBStudentId: string;

  test.beforeAll(async ({ request }) => {
    const [resA, resB] = await Promise.all([
      apiLogin(request, 'mentor_org_a', 'E2eTest@123!'),
      apiLogin(request, 'mentor_org_b', 'E2eTest@123!'),
    ]);
    mentorAToken = resA.accessToken;
    mentorBToken = resB.accessToken;

    // Get a student from each org via admin
    const adminRes = await apiLogin(request, 'admin', 'Admin@123!');
    const adminToken = adminRes.accessToken;

    const [orgARes, orgBRes] = await Promise.all([
      request.get(`${API}/api/v1/admin/users`, {
        headers: { Authorization: `Bearer ${adminToken}` },
        params: { orgId: 'org-a-id', role: 'STUDENT', size: 1 },
      }),
      request.get(`${API}/api/v1/admin/users`, {
        headers: { Authorization: `Bearer ${adminToken}` },
        params: { orgId: 'org-b-id', role: 'STUDENT', size: 1 },
      }),
    ]);
    const orgABody = await orgARes.json();
    const orgBBody = await orgBRes.json();
    orgAStudentId = orgABody.content?.[0]?.id;
    orgBStudentId = orgBBody.content?.[0]?.id;
  });

  test('6a: Org-A mentor can see Org-A student sessions', async ({ request }) => {
    if (!orgAStudentId) test.skip();
    const res = await request.get(`${API}/api/v1/sessions`, {
      headers: { Authorization: `Bearer ${mentorAToken}` },
      params: { studentId: orgAStudentId },
    });
    expect(res.status()).toBe(200);
  });

  test('6b: Org-A mentor cannot see Org-B student sessions (403)', async ({ request }) => {
    if (!orgBStudentId) test.skip();
    const res = await request.get(`${API}/api/v1/sessions`, {
      headers: { Authorization: `Bearer ${mentorAToken}` },
      params: { studentId: orgBStudentId },
    });
    expect(res.status()).toBe(403);
  });

  test('6c: Org-B mentor cannot see Org-A student sessions (403)', async ({ request }) => {
    if (!orgAStudentId) test.skip();
    const res = await request.get(`${API}/api/v1/sessions`, {
      headers: { Authorization: `Bearer ${mentorBToken}` },
      params: { studentId: orgAStudentId },
    });
    expect(res.status()).toBe(403);
  });

  test('6d: Org-B mentor can see own org students', async ({ request }) => {
    if (!orgBStudentId) test.skip();
    const res = await request.get(`${API}/api/v1/sessions`, {
      headers: { Authorization: `Bearer ${mentorBToken}` },
      params: { studentId: orgBStudentId },
    });
    expect(res.status()).toBe(200);
  });
});
