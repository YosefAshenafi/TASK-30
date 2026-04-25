-- Missing test fixtures required by integration test suites.
-- V203 extends the local seed data without modifying existing V200/V204
-- migrations (which would break Flyway checksums on environments that
-- already applied those versions).

-- Activity 000000000300 is referenced by SessionLifecycleApiTest as the
-- activityId when creating a session_activity_set.  The same UUID is used
-- for a cohort in V200 but that is a different table, so there is no
-- conflict.
INSERT INTO activities (id, course_id, name, description, sort_order)
VALUES ('00000000-0000-0000-0000-000000000300',
        '00000000-0000-0000-0000-000000000200',
        'CPR Practice Set',
        'Hands-on CPR practice activity for session sets',
        0)
ON CONFLICT (id) DO NOTHING;

-- A PENDING approval request that TrueNoMockHttpApiTest.adminApprovals_approve_
-- knownPendingId_withAdminJwt_returns200 expects to exist so it can approve
-- it and receive HTTP 200.
INSERT INTO approval_requests (id, type, payload, status, requested_by, expires_at)
VALUES ('00000000-0000-0000-0000-000000000aa1',
        'PERMISSION_CHANGE',
        '{}',
        'PENDING',
        '00000000-0000-0000-0000-000000000100',
        NOW() + INTERVAL '90 days')
ON CONFLICT (id) DO NOTHING;
