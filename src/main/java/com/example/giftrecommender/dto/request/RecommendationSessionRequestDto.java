package com.example.giftrecommender.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record RecommendationSessionRequestDto(
        @Schema(description = "게스트 ID", example = "a1b2c3d4-e5f6-7890-1234-56789abcdef0")
        UUID guestId
) {}
