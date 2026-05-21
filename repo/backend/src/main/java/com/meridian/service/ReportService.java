package com.meridian.service;

import com.meridian.dto.AnalyticsDto.AnalyticsFilterParams;
import com.meridian.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final JdbcTemplate jdbcTemplate;

    public ReportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> getEnrollments(UUID orgId, AnalyticsFilterParams filters) {
        StringBuilder sql = new StringBuilder(
                "SELECT u.username, c.title, c.version, e.enrolled_at, e.completed_at " +
                "FROM enrollments e " +
                "JOIN users u ON e.user_id = u.id " +
                "JOIN courses c ON e.course_id = c.id " +
                "WHERE 1=1 "
        );
        List<Object> params = new ArrayList<>();

        if (orgId != null) {
            sql.append(" AND u.organization_id = ?");
            params.add(orgId);
        }
        appendDateFilter(sql, params, filters);

        log.debug("getEnrollments query: {}", sql);
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> getSeatUtilization(UUID orgId, AnalyticsFilterParams filters) {
        StringBuilder sql = new StringBuilder(
                "SELECT c.title, c.capacity, COUNT(e.id) AS enrolled, " +
                "ROUND(100.0 * COUNT(e.id) / NULLIF(c.capacity, 0), 1) AS pct " +
                "FROM courses c " +
                "LEFT JOIN enrollments e ON c.id = e.course_id " +
                "WHERE c.deleted_at IS NULL "
        );
        List<Object> params = new ArrayList<>();

        if (orgId != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM users u WHERE u.id = e.user_id AND u.organization_id = ?)");
            params.add(orgId);
        }

        sql.append(" GROUP BY c.id, c.title, c.capacity ORDER BY pct DESC NULLS LAST");

        log.debug("getSeatUtilization query: {}", sql);
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> getRefunds(UUID orgId, AnalyticsFilterParams filters) {
        StringBuilder sql = new StringBuilder(
                "SELECT u.username, c.title, e.refunded_at " +
                "FROM enrollments e " +
                "JOIN users u ON e.user_id = u.id " +
                "JOIN courses c ON e.course_id = c.id " +
                "WHERE e.refunded_at IS NOT NULL "
        );
        List<Object> params = new ArrayList<>();

        if (orgId != null) {
            sql.append(" AND u.organization_id = ?");
            params.add(orgId);
        }
        appendDateFilter(sql, params, filters);
        sql.append(" ORDER BY e.refunded_at DESC");

        log.debug("getRefunds query: {}", sql);
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> getInventory(UUID orgId, AnalyticsFilterParams filters) {
        StringBuilder sql = new StringBuilder(
                "SELECT tm.name, tm.quantity_on_hand, tm.reorder_level, c.title AS course_title " +
                "FROM training_materials tm " +
                "LEFT JOIN courses c ON tm.course_id = c.id " +
                "WHERE 1=1 "
        );
        List<Object> params = new ArrayList<>();

        if (orgId != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM enrollments e JOIN users u ON e.user_id = u.id WHERE e.course_id = tm.course_id AND u.organization_id = ?)");
            params.add(orgId);
        }

        sql.append(" ORDER BY tm.name");

        log.debug("getInventory query: {}", sql);
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> getCertificationsExpiring(UUID orgId, int days) {
        StringBuilder sql = new StringBuilder(
                "SELECT u.username, cert.cert_name, cert.expires_at " +
                "FROM certifications cert " +
                "JOIN users u ON cert.user_id = u.id " +
                "WHERE cert.expires_at BETWEEN NOW() AND NOW() + (? * INTERVAL '1 day') "
        );
        List<Object> params = new ArrayList<>();
        params.add(days);

        if (orgId != null) {
            sql.append(" AND u.organization_id = ?");
            params.add(orgId);
        }

        sql.append(" ORDER BY cert.expires_at ASC");

        log.debug("getCertificationsExpiring query: {}", sql);
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    public UUID resolveOrgScope(User principal, UUID requestedOrgId) {
        boolean isCorporateMentor = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CORPORATE_MENTOR"));
        if (isCorporateMentor) {
            return principal.getOrganizationId();
        }
        return requestedOrgId;
    }

    private void appendDateFilter(StringBuilder sql, List<Object> params, AnalyticsFilterParams filters) {
        if (filters == null) return;
        if (filters.dateFrom() != null) {
            sql.append(" AND e.enrolled_at >= ?");
            params.add(filters.dateFrom());
        }
        if (filters.dateTo() != null) {
            sql.append(" AND e.enrolled_at <= ?");
            params.add(filters.dateTo());
        }
    }
}
