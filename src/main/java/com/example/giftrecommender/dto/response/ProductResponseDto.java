package com.example.giftrecommender.dto.response;

import java.util.UUID;

public record ProductResponseDto(
        UUID publicId,
        String title,
        String link,
        String image,
        int lprice,
        String mallName,
        String brand,
        String category3
) {}
