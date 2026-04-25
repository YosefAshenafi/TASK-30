package com.meridian.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final NamedParameterJdbcTemplate jdbc;

    public MasteryTrendSeries masteryTrends(AnalyticsFilter f) {
        String scope = f.learnerId() != null ? "LEARNER" :
                       f.cohortId() != null  ? "COHORT" : "COURSE";

        String sql = """
                SELECT DATE_TRUNC('day', aa.attempted_at) AS day,
                       COUNT(*) AS attempts,
                       CAST(SUM(CASE WHEN aa.is_correct THEN 1 ELSE 0 END) AS numeric) / COUNT(*) AS mastery
                FROM assessment_attempts aa
                JOIN assessment_items ai ON ai.id = aa.item_id
                WHERE aa.attempted_at IS NOT NULL
                  AND (CAST(:orgId AS uuid) IS NULL
                       OR EXISTS(SELECT 1 FROM users ou WHERE ou.id = aa.student_id AND ou.organization_id = CAST(:orgId AS uuid)))
                  AND (CAST(:fromTs AS timestamptz) IS NULL OR aa.attempted_at >= CAST(:fromTs AS timestamptz))
                  AND (CAST(:toTs AS timestamptz) IS NULL OR aa.attempted_at <= CAST(:toTs AS timestamptz))
                  AND (CAST(:courseId AS uuid) IS NULL OR ai.course_id = CAST(:courseId AS uuid))
                  AND (CAST(:learnerId AS uuid) IS NULL OR aa.student_id = CAST(:learnerId AS uuid))
                  AND (CAST(:cohortId AS uuid) IS NULL
                       OR EXISTS(SELECT 1 FROM enrollments enr WHERE enr.student_id = aa.student_id AND enr.cohort_id = CAST(:cohortId AS uuid)))
                  AND (CAST(:locationId AS uuid) IS NULL
                       OR EXISTS(SELECT 1 FROM courses cloc WHERE cloc.id = ai.course_id AND cloc.location_id = CAST(:locationId AS uuid)))
                  AND (CAST(:instructorId AS uuid) IS NULL
                       OR EXISTS(SELECT 1 FROM cohorts co2 JOIN enrollments enr2 ON enr2.cohort_id = co2.id
                                 JOIN courses coi ON coi.id = co2.course_id
                                 WHERE enr2.student_id = aa.student_id AND coi.instructor_id = CAST(:instructorId AS uuid)))
                  AND (:courseVersion IS NULL
                       OR EXISTS(SELECT 1 FROM courses cv WHERE cv.id = ai.course_id AND cv.version = :courseVersion))
                GROUP BY 1
                ORDER BY 1
                """;

        List<MasteryTrendSeries.Point> points = jdbc.query(sql, buildParams(f), (rs, i) -> new MasteryTrendSeries.Point(
                rs.getTimestamp("day").toInstant(),
                rs.getDouble("mastery") * 100.0,
                rs.getInt("attempts")));

        return new MasteryTrendSeries(scope, points);
    }

    public WrongAnswerDistribution wrongAnswers(AnalyticsFilter f) {
        String sql = """
                SELECT aa.item_id,
                       LEFT(ai.stem, 80) AS stem_preview,
                       CAST(aa.chosen_answer AS text) AS choice,
                       COUNT(*) AS cnt,
                       CAST(COUNT(*) AS numeric) / SUM(COUNT(*)) OVER (PARTITION BY aa.item_id) AS pct
                FROM assessment_attempts aa
                JOIN assessment_items ai ON ai.id = aa.item_id
                WHERE aa.is_correct = false
                  AND (CAST(:orgId AS uuid) IS NULL
                       OR EXISTS(SELECT 1 FROM users ou WHERE ou.id = aa.student_id AND ou.organization_id = CAST(:orgId AS uuid)))
                  AND (CAST(:fromTs AS timestamptz) IS NULL OR aa.attempted_at >= CAST(:fromTs AS timestamptz))
                  AND (CAST(:toTs AS timestamptz) IS NULL OR aa.attempted_at <= CAST(:toTs AS timestamptz))
                  AND (CAST(:courseId AS uuid) IS NULL OR ai.course_id = CAST(:courseId AS uuid))
                  AND (CAST(:learnerId AS uuid) IS NULL OR aa.student_id = CAST(:learnerId AS uuid))
                  AND (CAST(:cohortId AS uuid) IS NULL
                       OR EXISTS(SELECT 1 FROM enrollments enr WHERE enr.student_id = aa.student_id AND enr.cohort_id = CAST(:cohortId AS uuid)))
                  AND (CAST(:locationId AS uuid) IS NULL
                       OR EXISTS(SELECT 1 FROM courses cloc WHERE cloc.id = ai.course_id AND cloc.location_id = CAST(:locationId AS uuid)))
                  AND (CAST(:instructorId AS uuid) IS NULL
                       OR EXISTS(SELECT 1 FROM cohorts co2 JOIN enrollments enr2 ON enr2.cohort_id = co2.id
                                 JOIN courses coi ON coi.id = co2.course_id
                                 WHERE enr2.student_id = aa.student_id AND coi.instructor_id = CAST(:instructorId AS uuid)))
                  AND (:courseVersion IS NULL
                       OR EXISTS(SELECT 1 FROM courses cv WHERE cv.id = ai.course_id AND cv.version = :courseVersion))
                GROUP BY aa.item_id, ai.stem, CAST(aa.chosen_answer AS text)
                ORDER BY cnt DESC
                LIMIT 200
""";

        List<WrongAnswerDistribution.Item> items = jdbc.query(sql, buildParams(f), (rs, i) -> new WrongAnswerDistribution.Item(
                UUID.fromString(rs.getString("item_id")),
                rs.getString("stem_preview"),
                rs.getString("choice"),
                rs.getLong("cnt"),
                rs.getDouble("pct")));

        return new WrongAnswerDistribution(items);
    }

    public WeakKnowledgePointList weakKnowledgePoints(AnalyticsFilter f) {
        String sql = """
                SELECT kp.id AS kp_id, kp.name,
                       AVG(CASE WHEN aa.is_correct THEN 1.0 ELSE 0.0 END) * 100 AS mastery_pct,
                       COUNT(*) AS volume
                FROM assessment_attempts aa
                JOIN assessment_items ai ON ai.id = aa.item_id
                JOIN knowledge_points kp ON kp.id = ai.knowledge_point_id
                WHERE kp.id IS NOT NULL
                  AND (CAST(:orgId AS uuid) IS NULL
                       OR EXISTS(SELECT 1 FROM users ou WHERE ou.id = aa.student_id AND ou.organization_id = CAST(:orgId AS uuid)))
                  AND (CAST(:fromTs AS timestamptz) IS NULL OR aa.attempted_at >= CAST(:fromTs AS timestamptz))
                  AND (CAST(:toTs AS timestamptz) IS NULL OR aa.attempted_at <= CAST(:toTs AS timestamptz))
                  AND (CAST(:courseId AS uuid) IS NULL OR ai.course_id = CAST(:courseId AS uuid))
                  AND (CAST(:learnerId AS uuid) IS NULL OR aa.student_id = CAST(:learnerId AS uuid))
                  AND (CAST(:cohortId AS uuid) IS NULL
                       OR EXISTS(SELECT 1 FROM enrollments enr WHERE enr.student_id = aa.student_id AND enr.cohort_id = CAST(:cohortId AS uuid)))
                  AND (CAST(:locationId AS uuid) IS NULL
                       OR EXISTS(SELECT 1 FROM courses cloc WHERE cloc.id = ai.course_id AND cloc.location_id = CAST(:locationId AS uuid)))
                  AND (CAST(:instructorId AS uuid) IS NULL
                       OR EXISTS(SELECT 1 FROM cohorts co2 JOIN enrollments enr2 ON enr2.cohort_id = co2.id
                                 JOIN courses coi ON coi.id = co2.course_id
                                 WHERE enr2.student_id = aa.student_id AND coi.instructor_id = CAST(:instructorId AS uuid)))
                  AND (:courseVersion IS NULL
                       OR EXISTS(SELECT 1 FROM courses cv WHERE cv.id = ai.course_id AND cv.version = :courseVersion))
                GROUP BY kp.id, kp.name
                ORDER BY mastery_pct ASC
                LIMIT 50
                """;

        List<WeakKnowledgePointList.Item> items = jdbc.query(sql, buildParams(f), (rs, i) -> new WeakKnowledgePointList.Item(
                UUID.fromString(rs.getString("kp_id")),
                rs.getString("name"),
                rs.getDouble("mastery_pct"),
                rs.getLong("volume")));

        return new WeakKnowledgePointList(items);
    }

    public ItemStatsList itemStats(AnalyticsFilter f) {
        String sql = """
                SELECT ai.id AS item_id, ai.difficulty, ai.discrimination, COUNT(aa.id) AS attempts
                FROM assessment_items ai
                LEFT JOIN assessment_attempts aa ON aa.item_id = ai.id
                    AND (CAST(:fromTs AS timestamptz) IS NULL OR aa.attempted_at >= CAST(:fromTs AS timestamptz))
                    AND (CAST(:toTs AS timestamptz) IS NULL OR aa.attempted_at <= CAST(:toTs AS timestamptz))
                WHERE ai.deleted_at IS NULL
                  AND (CAST(:orgId AS uuid) IS NULL
                       OR aa.id IS NULL
                       OR EXISTS(SELECT 1 FROM users ou WHERE ou.id = aa.student_id AND ou.organization_id = CAST(:orgId AS uuid)))
                  AND (CAST(:courseId AS uuid) IS NULL OR ai.course_id = CAST(:courseId AS uuid))
                  AND (CAST(:cohortId AS uuid) IS NULL
                       OR EXISTS(SELECT 1 FROM enrollments enr WHERE enr.student_id = aa.student_id AND enr.cohort_id = CAST(:cohortId AS uuid)))
                  AND (CAST(:locationId AS uuid) IS NULL
                       OR EXISTS(SELECT 1 FROM courses cloc WHERE cloc.id = ai.course_id AND cloc.location_id = CAST(:locationId AS uuid)))
                  AND (CAST(:instructorId AS uuid) IS NULL
                       OR EXISTS(SELECT 1 FROM cohorts co2 JOIN enrollments enr2 ON enr2.cohort_id = co2.id
                                 JOIN courses coi ON coi.id = co2.course_id
                                 WHERE enr2.student_id = aa.student_id AND coi.instructor_id = CAST(:instructorId AS uuid)))
                  AND (:courseVersion IS NULL
                       OR EXISTS(SELECT 1 FROM courses cv WHERE cv.id = ai.course_id AND cv.version = :courseVersion))
                GROUP BY ai.id, ai.difficulty, ai.discrimination
                ORDER BY attempts DESC
                LIMIT 200
                """;

        List<ItemStatsList.Item> items = jdbc.query(sql, buildParams(f), (rs, i) -> new ItemStatsList.Item(
                UUID.fromString(rs.getString("item_id")),
                rs.getDouble("difficulty"),
                rs.getDouble("discrimination"),
                rs.getLong("attempts")));

        return new ItemStatsList(items);
    }

    private MapSqlParameterSource buildParams(AnalyticsFilter f) {
        return new MapSqlParameterSource()
                .addValue("orgId", f.organizationId() != null ? f.organizationId().toString() : null)
                .addValue("fromTs", f.from() != null ? f.from().toString() : null)
                .addValue("toTs", f.to() != null ? f.to().toString() : null)
                .addValue("courseId", f.courseId() != null ? f.courseId().toString() : null)
                .addValue("learnerId", f.learnerId() != null ? f.learnerId().toString() : null)
                .addValue("cohortId", f.cohortId() != null ? f.cohortId().toString() : null)
                .addValue("locationId", f.locationId() != null ? f.locationId().toString() : null)
                .addValue("instructorId", f.instructorId() != null ? f.instructorId().toString() : null)
                .addValue("courseVersion", f.courseVersion());
    }
}
