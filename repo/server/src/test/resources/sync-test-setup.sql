INSERT INTO users (id, username, display_name, password_bcrypt, role, status, organization_id)
VALUES ('00000000-0000-0000-0000-000000000002', 'sync_test_student', 'Sync Test Student',
        '$2a$12$placeholder', 'STUDENT', 'ACTIVE', '00000000-0000-0000-0000-000000000001')
ON CONFLICT DO NOTHING;

INSERT INTO courses (id, code, title, version, location_id, instructor_id, classification)
VALUES ('00000000-0000-0000-0000-000000000c10', 'SYNC-TEST-001', 'Sync Test Course', '1.0',
        '00000000-0000-0000-0000-000000000010', '00000000-0000-0000-0000-000000000020', 'INTERNAL')
ON CONFLICT DO NOTHING;

INSERT INTO activities (id, course_id, name, description, sort_order)
VALUES ('00000000-0000-0000-0000-000000000a01', '00000000-0000-0000-0000-000000000c10',
        'Test Activity', 'Test activity description', 1)
ON CONFLICT DO NOTHING;
