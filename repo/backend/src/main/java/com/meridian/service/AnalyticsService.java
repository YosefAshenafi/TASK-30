package com.meridian.service;

import com.meridian.dto.AnalyticsDto.AnalyticsFilterParams;
import com.meridian.dto.AnalyticsDto.ItemDifficultyItem;
import com.meridian.dto.AnalyticsDto.KnowledgeGapItem;
import com.meridian.dto.AnalyticsDto.MasteryTrendPoint;
import com.meridian.dto.AnalyticsDto.WrongAnswerItem;
import com.meridian.exception.AppException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(AnalyticsService.class);

    private final EntityManager entityManager;

    public AnalyticsService(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<MasteryTrendPoint> getMasteryTrends(AnalyticsFilterParams filters) {
        StringBuilder sql = new StringBuilder(
                "SELECT TO_CHAR(DATE_TRUNC('week', a.attempted_at), 'YYYY-MM-DD') AS week, " +
                "ROUND(100.0 * COUNT(*) FILTER(WHERE a.is_correct) / NULLIF(COUNT(*), 0), 2) AS mastery_rate, " +
                "COUNT(*) AS total_attempts " +
                "FROM attempts a " +
                "JOIN users u ON a.user_id = u.id " +
                "JOIN assessment_items ai ON a.assessment_item_id = ai.id " +
                "JOIN courses c ON ai.course_id = c.id " +
                "WHERE 1=1 "
        );
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, filters);
        sql.append(" GROUP BY 1 ORDER BY 1");

        Query query = entityManager.createNativeQuery(sql.toString());
        bindParams(query, params);

        List<Object[]> rows = query.getResultList();
        List<MasteryTrendPoint> result = new ArrayList<>();
        for (Object[] row : rows) {
            String week = (String) row[0];
            double masteryRate = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
            long totalAttempts = ((Number) row[2]).longValue();
            result.add(new MasteryTrendPoint(week, masteryRate, totalAttempts));
        }
        return result;
    }

    public List<WrongAnswerItem> getWrongAnswerDistribution(AnalyticsFilterParams filters) {
        StringBuilder sql = new StringBuilder(
                "SELECT ai.id, ai.question, ai.knowledge_point, " +
                "COUNT(*) AS wrong_count, " +
                "ROUND(100.0 * COUNT(*) / NULLIF((SELECT COUNT(*) FROM attempts a2 WHERE a2.assessment_item_id = ai.id), 0), 2) AS wrong_rate " +
                "FROM attempts a " +
                "JOIN users u ON a.user_id = u.id " +
                "JOIN assessment_items ai ON a.assessment_item_id = ai.id " +
                "JOIN courses c ON ai.course_id = c.id " +
                "WHERE a.is_correct = false "
        );
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, filters);
        sql.append(" GROUP BY ai.id, ai.question, ai.knowledge_point ORDER BY wrong_count DESC");

        Query query = entityManager.createNativeQuery(sql.toString());
        bindParams(query, params);

        List<Object[]> rows = query.getResultList();
        List<WrongAnswerItem> result = new ArrayList<>();
        for (Object[] row : rows) {
            UUID itemId = toUUID(row[0]);
            String question = (String) row[1];
            String knowledgePoint = (String) row[2];
            long wrongCount = ((Number) row[3]).longValue();
            double wrongRate = row[4] != null ? ((Number) row[4]).doubleValue() : 0.0;
            result.add(new WrongAnswerItem(itemId, question, knowledgePoint, wrongCount, wrongRate));
        }
        return result;
    }

    public List<KnowledgeGapItem> getKnowledgeGaps(AnalyticsFilterParams filters) {
        StringBuilder sql = new StringBuilder(
                "SELECT ai.knowledge_point, " +
                "ROUND(100.0 * COUNT(*) FILTER(WHERE a.is_correct = false) / NULLIF(COUNT(*), 0), 2) AS wrong_rate, " +
                "COUNT(*) AS total_attempts " +
                "FROM attempts a " +
                "JOIN users u ON a.user_id = u.id " +
                "JOIN assessment_items ai ON a.assessment_item_id = ai.id " +
                "JOIN courses c ON ai.course_id = c.id " +
                "WHERE 1=1 "
        );
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, filters);
        sql.append(" GROUP BY ai.knowledge_point ORDER BY wrong_rate DESC");

        Query query = entityManager.createNativeQuery(sql.toString());
        bindParams(query, params);

        List<Object[]> rows = query.getResultList();
        List<KnowledgeGapItem> result = new ArrayList<>();
        for (Object[] row : rows) {
            String knowledgePoint = (String) row[0];
            double wrongRate = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
            long totalAttempts = ((Number) row[2]).longValue();
            result.add(new KnowledgeGapItem(knowledgePoint, wrongRate, totalAttempts));
        }
        return result;
    }

    public List<ItemDifficultyItem> getItemDifficulty(AnalyticsFilterParams filters) {
        StringBuilder sql = new StringBuilder(
                "SELECT ai.id, ai.question, ai.difficulty, ai.discrimination, " +
                "ROUND(1.0 - (COUNT(*) FILTER(WHERE a.is_correct) * 1.0 / NULLIF(COUNT(*), 0)), 4) AS observed_difficulty, " +
                "COUNT(*) AS total_attempts " +
                "FROM assessment_items ai " +
                "LEFT JOIN attempts a ON ai.id = a.assessment_item_id " +
                "LEFT JOIN users u ON a.user_id = u.id " +
                "LEFT JOIN courses c ON ai.course_id = c.id " +
                "WHERE 1=1 "
        );
        List<Object> params = new ArrayList<>();
        appendFilters(sql, params, filters);
        sql.append(" GROUP BY ai.id, ai.question, ai.difficulty, ai.discrimination ORDER BY observed_difficulty DESC");

        Query query = entityManager.createNativeQuery(sql.toString());
        bindParams(query, params);

        List<Object[]> rows = query.getResultList();
        List<ItemDifficultyItem> result = new ArrayList<>();
        for (Object[] row : rows) {
            UUID itemId = toUUID(row[0]);
            String question = (String) row[1];
            double storedDifficulty = row[2] != null ? ((Number) row[2]).doubleValue() : 0.5;
            double discrimination = row[3] != null ? ((Number) row[3]).doubleValue() : 0.5;
            double observedDifficulty = row[4] != null ? ((Number) row[4]).doubleValue() : 0.0;
            long totalAttempts = ((Number) row[5]).longValue();
            result.add(new ItemDifficultyItem(itemId, question, storedDifficulty, observedDifficulty, discrimination, totalAttempts));
        }
        return result;
    }

    public Map<String, Object> getLearnerAnalytics(UUID learnerId, UUID requestingUserId,
                                                    String requestingUserRole,
                                                    UUID requestingUserOrgId,
                                                    AnalyticsFilterParams filters) {
        if ("ROLE_CORPORATE_MENTOR".equals(requestingUserRole)) {
            String orgCheckSql = "SELECT organization_id FROM users WHERE id = :learnerId AND deleted_at IS NULL";
            Query orgQuery = entityManager.createNativeQuery(orgCheckSql);
            orgQuery.setParameter("learnerId", learnerId);
            Object orgIdResult = orgQuery.getResultList().stream().findFirst().orElse(null);

            if (orgIdResult == null) {
                throw AppException.notFound("Learner not found: " + learnerId);
            }

            UUID learnerOrgId = toUUID(orgIdResult);
            if (learnerOrgId == null || !learnerOrgId.equals(requestingUserOrgId)) {
                throw AppException.forbidden("Corporate mentor can only view analytics for learners in their organization");
            }
        }

        AnalyticsFilterParams learnerFilters = new AnalyticsFilterParams(
                filters.dateFrom(),
                filters.dateTo(),
                filters.location(),
                filters.instructorId(),
                filters.courseVersion(),
                filters.organizationId()
        );

        Map<String, Object> analytics = new HashMap<>();
        analytics.put("masteryTrends", getMasteryTrendsForLearner(learnerId, learnerFilters));
        analytics.put("wrongAnswers", getWrongAnswersForLearner(learnerId, learnerFilters));
        analytics.put("knowledgeGaps", getKnowledgeGapsForLearner(learnerId, learnerFilters));
        analytics.put("itemDifficulty", getItemDifficultyForLearner(learnerId, learnerFilters));
        return analytics;
    }

    private List<MasteryTrendPoint> getMasteryTrendsForLearner(UUID learnerId, AnalyticsFilterParams filters) {
        StringBuilder sql = new StringBuilder(
                "SELECT TO_CHAR(DATE_TRUNC('week', a.attempted_at), 'YYYY-MM-DD') AS week, " +
                "ROUND(100.0 * COUNT(*) FILTER(WHERE a.is_correct) / NULLIF(COUNT(*), 0), 2) AS mastery_rate, " +
                "COUNT(*) AS total_attempts " +
                "FROM attempts a " +
                "JOIN users u ON a.user_id = u.id " +
                "JOIN assessment_items ai ON a.assessment_item_id = ai.id " +
                "JOIN courses c ON ai.course_id = c.id " +
                "WHERE a.user_id = ?1 "
        );
        List<Object> params = new ArrayList<>();
        params.add(learnerId);
        appendFiltersWithOffset(sql, params, filters, 1);
        sql.append(" GROUP BY 1 ORDER BY 1");

        Query query = entityManager.createNativeQuery(sql.toString());
        bindParams(query, params);

        List<Object[]> rows = query.getResultList();
        List<MasteryTrendPoint> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new MasteryTrendPoint(
                    (String) row[0],
                    row[1] != null ? ((Number) row[1]).doubleValue() : 0.0,
                    ((Number) row[2]).longValue()
            ));
        }
        return result;
    }

    private List<WrongAnswerItem> getWrongAnswersForLearner(UUID learnerId, AnalyticsFilterParams filters) {
        StringBuilder sql = new StringBuilder(
                "SELECT ai.id, ai.question, ai.knowledge_point, " +
                "COUNT(*) AS wrong_count, " +
                "ROUND(100.0 * COUNT(*) / NULLIF((SELECT COUNT(*) FROM attempts a2 WHERE a2.assessment_item_id = ai.id AND a2.user_id = ?1), 0), 2) AS wrong_rate " +
                "FROM attempts a " +
                "JOIN assessment_items ai ON a.assessment_item_id = ai.id " +
                "JOIN courses c ON ai.course_id = c.id " +
                "WHERE a.is_correct = false AND a.user_id = ?1 "
        );
        List<Object> params = new ArrayList<>();
        params.add(learnerId);
        appendFiltersWithOffset(sql, params, filters, 1);
        sql.append(" GROUP BY ai.id, ai.question, ai.knowledge_point ORDER BY wrong_count DESC");

        Query query = entityManager.createNativeQuery(sql.toString());
        bindParams(query, params);

        List<Object[]> rows = query.getResultList();
        List<WrongAnswerItem> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new WrongAnswerItem(
                    toUUID(row[0]),
                    (String) row[1],
                    (String) row[2],
                    ((Number) row[3]).longValue(),
                    row[4] != null ? ((Number) row[4]).doubleValue() : 0.0
            ));
        }
        return result;
    }

    private List<KnowledgeGapItem> getKnowledgeGapsForLearner(UUID learnerId, AnalyticsFilterParams filters) {
        StringBuilder sql = new StringBuilder(
                "SELECT ai.knowledge_point, " +
                "ROUND(100.0 * COUNT(*) FILTER(WHERE a.is_correct = false) / NULLIF(COUNT(*), 0), 2) AS wrong_rate, " +
                "COUNT(*) AS total_attempts " +
                "FROM attempts a " +
                "JOIN assessment_items ai ON a.assessment_item_id = ai.id " +
                "JOIN courses c ON ai.course_id = c.id " +
                "WHERE a.user_id = ?1 "
        );
        List<Object> params = new ArrayList<>();
        params.add(learnerId);
        appendFiltersWithOffset(sql, params, filters, 1);
        sql.append(" GROUP BY ai.knowledge_point ORDER BY wrong_rate DESC");

        Query query = entityManager.createNativeQuery(sql.toString());
        bindParams(query, params);

        List<Object[]> rows = query.getResultList();
        List<KnowledgeGapItem> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new KnowledgeGapItem(
                    (String) row[0],
                    row[1] != null ? ((Number) row[1]).doubleValue() : 0.0,
                    ((Number) row[2]).longValue()
            ));
        }
        return result;
    }

    private List<ItemDifficultyItem> getItemDifficultyForLearner(UUID learnerId, AnalyticsFilterParams filters) {
        StringBuilder sql = new StringBuilder(
                "SELECT ai.id, ai.question, ai.difficulty, ai.discrimination, " +
                "ROUND(1.0 - (COUNT(*) FILTER(WHERE a.is_correct) * 1.0 / NULLIF(COUNT(*), 0)), 4) AS observed_difficulty, " +
                "COUNT(*) AS total_attempts " +
                "FROM assessment_items ai " +
                "LEFT JOIN attempts a ON ai.id = a.assessment_item_id AND a.user_id = ?1 " +
                "LEFT JOIN courses c ON ai.course_id = c.id " +
                "WHERE 1=1 "
        );
        List<Object> params = new ArrayList<>();
        params.add(learnerId);
        appendFiltersWithOffset(sql, params, filters, 1);
        sql.append(" GROUP BY ai.id, ai.question, ai.difficulty, ai.discrimination ORDER BY observed_difficulty DESC");

        Query query = entityManager.createNativeQuery(sql.toString());
        bindParams(query, params);

        List<Object[]> rows = query.getResultList();
        List<ItemDifficultyItem> result = new ArrayList<>();
        for (Object[] row : rows) {
            result.add(new ItemDifficultyItem(
                    toUUID(row[0]),
                    (String) row[1],
                    row[2] != null ? ((Number) row[2]).doubleValue() : 0.5,
                    row[4] != null ? ((Number) row[4]).doubleValue() : 0.0,
                    row[3] != null ? ((Number) row[3]).doubleValue() : 0.5,
                    ((Number) row[5]).longValue()
            ));
        }
        return result;
    }

    void appendFilters(StringBuilder sb, List<Object> params, AnalyticsFilterParams f) {
        if (f == null) return;
        int pos = params.size() + 1;
        if (f.dateFrom() != null) {
            sb.append(" AND a.attempted_at >= ?").append(pos++);
            params.add(f.dateFrom().atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
        }
        if (f.dateTo() != null) {
            sb.append(" AND a.attempted_at <= ?").append(pos++);
            params.add(f.dateTo().atTime(23, 59, 59).toInstant(java.time.ZoneOffset.UTC));
        }
        if (f.location() != null && !f.location().isBlank()) {
            sb.append(" AND c.location = ?").append(pos++);
            params.add(f.location());
        }
        if (f.courseVersion() != null && !f.courseVersion().isBlank()) {
            sb.append(" AND c.version = ?").append(pos++);
            params.add(f.courseVersion());
        }
        if (f.organizationId() != null) {
            sb.append(" AND u.organization_id = ?").append(pos);
            params.add(f.organizationId());
        }
    }

    private void appendFiltersWithOffset(StringBuilder sb, List<Object> params,
                                          AnalyticsFilterParams f, int alreadyBound) {
        if (f == null) return;
        int pos = alreadyBound + 1;
        if (f.dateFrom() != null) {
            sb.append(" AND a.attempted_at >= ?").append(pos++);
            params.add(f.dateFrom().atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
        }
        if (f.dateTo() != null) {
            sb.append(" AND a.attempted_at <= ?").append(pos++);
            params.add(f.dateTo().atTime(23, 59, 59).toInstant(java.time.ZoneOffset.UTC));
        }
        if (f.location() != null && !f.location().isBlank()) {
            sb.append(" AND c.location = ?").append(pos++);
            params.add(f.location());
        }
        if (f.courseVersion() != null && !f.courseVersion().isBlank()) {
            sb.append(" AND c.version = ?").append(pos++);
            params.add(f.courseVersion());
        }
        if (f.organizationId() != null) {
            sb.append(" AND u.organization_id = ?").append(pos);
            params.add(f.organizationId());
        }
    }

    private void bindParams(Query query, List<Object> params) {
        for (int i = 0; i < params.size(); i++) {
            query.setParameter(i + 1, params.get(i));
        }
    }

    private UUID toUUID(Object value) {
        if (value == null) return null;
        if (value instanceof UUID) return (UUID) value;
        return UUID.fromString(value.toString());
    }
}
