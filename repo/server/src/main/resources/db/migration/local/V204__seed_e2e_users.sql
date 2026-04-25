-- Seed fixtures for E2E tests covering org-scope isolation and the recycle
-- bin round-trip. Kept in a separate forward migration (V204) so existing
-- environments that already ran V200 pick up the new data without breaking
-- Flyway's migration-history checksum for V200.

INSERT INTO organizations (id, name, code) VALUES
('00000000-0000-0000-0000-000000000002', 'Partner Org', 'PARTNER')
ON CONFLICT (id) DO NOTHING;

INSERT INTO users (id, username, display_name, password_bcrypt, role, status, organization_id) VALUES
('00000000-0000-0000-0000-000000000105', 'mentor_org_a',   'Mentor Org A',
 crypt('E2eTest@123!', gen_salt('bf', 12)), 'CORPORATE_MENTOR', 'ACTIVE',
 '00000000-0000-0000-0000-000000000001'),
('00000000-0000-0000-0000-000000000106', 'mentor_org_b',   'Mentor Org B',
 crypt('E2eTest@123!', gen_salt('bf', 12)), 'CORPORATE_MENTOR', 'ACTIVE',
 '00000000-0000-0000-0000-000000000002'),
('00000000-0000-0000-0000-000000000107', 'student_org_b',  'Eve Partner',
 crypt('E2eTest@123!', gen_salt('bf', 12)), 'STUDENT',          'ACTIVE',
 '00000000-0000-0000-0000-000000000002'),
('00000000-0000-0000-0000-000000000108', 'student_e2e_recycle', 'Frank Recycled',
 crypt('E2eTest@123!', gen_salt('bf', 12)), 'STUDENT',          'ACTIVE',
 '00000000-0000-0000-0000-000000000001')
ON CONFLICT (id) DO NOTHING;
