package com.meridian.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "meridian.jwt")
public record JwtProperties(
        String accessSecret,
        long accessExpiryMs,
        String refreshSecret,
        long refreshExpiryMs
) {}
