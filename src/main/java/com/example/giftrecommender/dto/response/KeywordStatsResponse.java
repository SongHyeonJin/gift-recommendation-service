package com.example.giftrecommender.dto.response;

import java.util.Map;

public record KeywordStatsResponse(
        int totalCount,
        Map<String, Long> genderStats,
        Map<String, Long> ageStats,
        Map<String, Long> priceStats
) {}
