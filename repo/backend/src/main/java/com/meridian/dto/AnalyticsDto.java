package com.meridian.dto;

import java.time.LocalDate;
import java.util.UUID;

public class AnalyticsDto {

    public record AnalyticsFilterParams(
            LocalDate dateFrom,
            LocalDate dateTo,
            String location,
            UUID instructorId,
            String courseVersion,
            UUID organizationId
    ) {}

    public record MasteryTrendPoint(
            String week,
            double masteryRate,
            long totalAttempts
    ) {}

    public record WrongAnswerItem(
            UUID itemId,
            String question,
            String knowledgePoint,
            long wrongCount,
            double wrongRate
    ) {}

    public record KnowledgeGapItem(
            String knowledgePoint,
            double wrongRate,
            long totalAttempts
    ) {}

    public record ItemDifficultyItem(
            UUID itemId,
            String question,
            double storedDifficulty,
            double observedDifficulty,
            double discrimination,
            long totalAttempts
    ) {}
}
