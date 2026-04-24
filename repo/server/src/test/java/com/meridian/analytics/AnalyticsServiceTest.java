package com.meridian.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    @InjectMocks
    private AnalyticsService service;

    private AnalyticsFilter emptyFilter;

    @BeforeEach
    void setUp() {
        emptyFilter = new AnalyticsFilter(null, null, null, null, null, null, null, null);
    }

    @Test
    void masteryTrends_returnsSeriesWithCourseScope_whenNoLearnerOrCohort() {
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        MasteryTrendSeries result = service.masteryTrends(emptyFilter);

        assertThat(result.scope()).isEqualTo("COURSE");
        assertThat(result.points()).isEmpty();
    }

    @Test
    void masteryTrends_withLearner_setsLearnerScope() {
        AnalyticsFilter f = new AnalyticsFilter(null, null, null, null,
                null, null, null, UUID.randomUUID());
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        MasteryTrendSeries result = service.masteryTrends(f);

        assertThat(result.scope()).isEqualTo("LEARNER");
    }

    @Test
    void masteryTrends_withCohort_setsCohortScope() {
        AnalyticsFilter f = new AnalyticsFilter(null, null, null, null,
                null, null, UUID.randomUUID(), null);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        MasteryTrendSeries result = service.masteryTrends(f);

        assertThat(result.scope()).isEqualTo("COHORT");
    }

    @Test
    void masteryTrends_mapsRowsToPoints() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getTimestamp("day")).thenReturn(Timestamp.from(Instant.parse("2024-01-01T00:00:00Z")));
        when(rs.getDouble("mastery")).thenReturn(0.75);
        when(rs.getInt("attempts")).thenReturn(10);

        ArgumentCaptor<RowMapper<MasteryTrendSeries.Point>> mapperCaptor =
                ArgumentCaptor.forClass(RowMapper.class);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), mapperCaptor.capture()))
                .thenReturn(List.of());

        service.masteryTrends(emptyFilter);
        MasteryTrendSeries.Point p = mapperCaptor.getValue().mapRow(rs, 0);

        assertThat(p.masteryPct()).isEqualTo(75.0);
        assertThat(p.attempts()).isEqualTo(10);
    }

    @Test
    void wrongAnswers_returnsDistribution() {
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        WrongAnswerDistribution result = service.wrongAnswers(emptyFilter);
        assertThat(result.items()).isEmpty();
    }

    @Test
    void weakKnowledgePoints_returnsList() {
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        WeakKnowledgePointList result = service.weakKnowledgePoints(emptyFilter);
        assertThat(result.items()).isEmpty();
    }

    @Test
    void itemStats_returnsEmptyListWhenNoData() {
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        ItemStatsList result = service.itemStats(emptyFilter);
        assertThat(result.items()).isEmpty();
    }

    @Test
    void buildParams_includesAllFilterFields() {
        AnalyticsFilter f = new AnalyticsFilter(
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-02-01T00:00:00Z"),
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "v2",
                UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID());

        ArgumentCaptor<MapSqlParameterSource> captor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        when(jdbc.query(anyString(), captor.capture(), any(RowMapper.class))).thenReturn(List.of());

        service.masteryTrends(f);

        MapSqlParameterSource values = captor.getValue();
        assertThat(values.getValue("courseVersion")).isEqualTo("v2");
        assertThat(values.getValue("orgId")).isEqualTo(f.organizationId().toString());
    }
}
