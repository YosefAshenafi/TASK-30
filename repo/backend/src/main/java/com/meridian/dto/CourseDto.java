package com.meridian.dto;

import java.util.UUID;

public record CourseDto(
        UUID id,
        String title,
        String version,
        String location,
        String instructor
) {}
