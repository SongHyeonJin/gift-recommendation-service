package com.example.giftrecommender.common.logging;

public final class LoggingExcludes {
    private LoggingExcludes() {}

    public static final String[] URL_PREFIXES = {
            "/v3/api-docs", "/swagger-ui", "/swagger-resources",
            "/webjars", "/favicon.ico", "/actuator", "/error", "/health"
    };

    public static final String API_PREFIX = "/api/";
}
