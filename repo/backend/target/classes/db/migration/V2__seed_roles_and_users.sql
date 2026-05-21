-- Seed roles
INSERT INTO roles(id, name) VALUES
    ('11111111-0000-0000-0000-000000000001', 'ROLE_STUDENT'),
    ('11111111-0000-0000-0000-000000000002', 'ROLE_CORPORATE_MENTOR'),
    ('11111111-0000-0000-0000-000000000003', 'ROLE_FACULTY_MENTOR'),
    ('11111111-0000-0000-0000-000000000004', 'ROLE_ADMINISTRATOR');

-- Seed organizations
INSERT INTO organizations(id, name) VALUES
    ('22222222-0000-0000-0000-000000000001', 'Acme Corporation'),
    ('22222222-0000-0000-0000-000000000002', 'Meridian Institute');

-- Seed demo users (passwords are BCrypt hashes)
-- admin     → Admin@12345678
-- faculty1  → Faculty@12345678
-- corp1     → Corp@12345678
-- student1  → Student@12345678
INSERT INTO users(id, username, password_hash, status, organization_id) VALUES
    ('33333333-0000-0000-0000-000000000001', 'admin',
     '$2a$12$LrHcAeSMCQJrjPqDZ9gfWO7hLX9o0xVzBHU3wHNXGXjVlV/o1.TJ6',
     'ACTIVE', NULL),
    ('33333333-0000-0000-0000-000000000002', 'faculty1',
     '$2a$12$aSC2LRXsxoX.dpLrVFiV5.MFML4fJxCkv9MxilD4axBiYqCWpXcey',
     'ACTIVE', '22222222-0000-0000-0000-000000000002'),
    ('33333333-0000-0000-0000-000000000003', 'corp1',
     '$2a$12$nNMhiJifmqXIJSTKFRXL1.xOovh4r/9AE85E0g3cVxFXDtEO3vgje',
     'ACTIVE', '22222222-0000-0000-0000-000000000001'),
    ('33333333-0000-0000-0000-000000000004', 'student1',
     '$2a$12$R9E0cW1X3z3o/rT7MPJpTOBzBuM7.NZd3PkuaHOEd.dOfPVs3J.S.',
     'ACTIVE', NULL);

-- Assign roles
INSERT INTO user_roles(user_id, role_id) VALUES
    ('33333333-0000-0000-0000-000000000001', '11111111-0000-0000-0000-000000000004'),
    ('33333333-0000-0000-0000-000000000002', '11111111-0000-0000-0000-000000000003'),
    ('33333333-0000-0000-0000-000000000003', '11111111-0000-0000-0000-000000000002'),
    ('33333333-0000-0000-0000-000000000004', '11111111-0000-0000-0000-000000000001');

-- Seed notification templates
INSERT INTO notification_templates(name, subject, body) VALUES
    ('account_approved',   'Your account has been approved',
     'Hello {{username}}, your Meridian account has been approved. You can now log in.'),
    ('account_rejected',   'Your account registration was not approved',
     'Hello {{username}}, your Meridian account request was not approved.'),
    ('export_complete',    'Your export is ready',
     'Hello {{username}}, your {{reportType}} export is ready at {{path}}.'),
    ('anomaly_alert',      '[Security] Anomaly detected on your account',
     'Hello {{username}}, we detected unusual activity: {{details}}. If this was not you, please contact your administrator.'),
    ('approval_request',   'Action required: Approval request',
     'Hello {{username}}, a new approval request ({{type}}) requires your review.');

-- Seed demo course
INSERT INTO courses(id, title, version, location, instructor, capacity) VALUES
    ('44444444-0000-0000-0000-000000000001', 'Foundations of Safety Training', '1.0',
     'Main Campus', 'faculty1', 30);

-- Seed demo assessment items
INSERT INTO assessment_items(course_id, question, correct_answer, knowledge_point, difficulty, discrimination)
VALUES
    ('44444444-0000-0000-0000-000000000001',
     'What is the primary purpose of PPE?',
     'To protect workers from hazards',
     'PPE Basics', 0.40, 0.65),
    ('44444444-0000-0000-0000-000000000001',
     'What does MSDS stand for?',
     'Material Safety Data Sheet',
     'Hazard Communication', 0.55, 0.70),
    ('44444444-0000-0000-0000-000000000001',
     'When should you report a near-miss?',
     'Immediately',
     'Incident Reporting', 0.30, 0.80);

-- Seed student enrollment
INSERT INTO enrollments(user_id, course_id)
VALUES ('33333333-0000-0000-0000-000000000004', '44444444-0000-0000-0000-000000000001');

-- Seed training materials
INSERT INTO training_materials(course_id, name, quantity_on_hand, reorder_level)
VALUES
    ('44444444-0000-0000-0000-000000000001', 'Safety Manual v1.0', 25, 5),
    ('44444444-0000-0000-0000-000000000001', 'PPE Kit',            15, 5);
