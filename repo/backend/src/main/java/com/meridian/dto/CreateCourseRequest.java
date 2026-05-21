package com.meridian.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCourseRequest(
        @NotBlank
        String title,

        String version,
        String location,
        String instructor,
        Integer capacity
) {}
