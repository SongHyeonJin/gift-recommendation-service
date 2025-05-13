package com.example.giftrecommender.dto.response;

import java.util.List;

public record RecommendationResponseDto(
        List<RecommendedProductResponseDto> products
) {}
