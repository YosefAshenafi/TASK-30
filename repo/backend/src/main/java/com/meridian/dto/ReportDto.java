package com.meridian.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public class ReportDto {

    public record CreateScheduleRequest(
            UUID orgId,
            @NotBlank(message = "reportType is required")
            String reportType,
            @NotBlank(message = "cronExpression is required")
            String cronExpression,
            @NotBlank(message = "outputFormat is required")
            String outputFormat,
            @NotBlank(message = "outputPath is required")
            String outputPath
    ) {}

    public record ExportRequest(
            @NotBlank(message = "reportType is required")
            String reportType,
            @NotBlank(message = "format is required")
            String format,
            UUID orgId,
            UUID approvalId
    ) {}

    public record ExportResponse(
            String path,
            String filename,
            long sizeBytes
    ) {}
}
