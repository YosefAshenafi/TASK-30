-- Demo assessment attempts so the Analytics dashboards (mastery trends, wrong
-- answers, knowledge gaps, item difficulty) render real data out of the box.
--
-- This is applied by Flyway during backend startup, i.e. automatically as part of
-- `docker compose up` with no extra command. Flyway records the migration, so it
-- runs exactly once per database and a second `docker compose up` is a no-op.
--
-- 48 attempts for the seeded student across the 3 seeded assessment items over the
-- last 4 weeks, with mastery improving toward the present week (25% -> 50% -> 75% ->
-- 100%) so the weekly mastery-trend chart shows a clear upward trend and the
-- wrong-answer / knowledge-gap tabs have incorrect attempts to aggregate.
INSERT INTO attempts (user_id, assessment_item_id, answer, is_correct, attempted_at)
SELECT
    u.id,
    ai.id,
    CASE WHEN n.idx > wk.week_offset THEN ai.correct_answer ELSE 'Incorrect demo response' END,
    (n.idx > wk.week_offset),
    NOW() - (wk.week_offset * INTERVAL '7 days') - (n.idx * INTERVAL '5 hours')
FROM assessment_items ai
JOIN courses c ON ai.course_id = c.id
JOIN users u ON u.username = 'student1'
CROSS JOIN (VALUES (0), (1), (2), (3)) AS wk(week_offset)
CROSS JOIN generate_series(1, 4) AS n(idx)
WHERE c.id = '44444444-0000-0000-0000-000000000001';
