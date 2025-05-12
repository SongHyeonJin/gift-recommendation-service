package com.example.giftrecommender.infra.coupang.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;

@Builder
public record CoupangProductResponseDto(
        Long coupangProductId,
        String title,
        String imageUrl,
        String productUrl,
        Integer price,
        String keyword,
        Integer rank,
        boolean isRocket,
        boolean isFreeShipping
) {
    public static CoupangProductResponseDto of(JsonNode item) {
        return CoupangProductResponseDto.builder()
                .coupangProductId(item.path("productId").asLong())
                .title(item.path("productName").asText())
                .imageUrl(item.path("productImage").asText())
                .productUrl(item.path("productUrl").asText())
                .price(item.path("productPrice").asInt())
                .keyword(item.path("keyword").asText())
                .rank(item.path("rank").asInt())
                .isRocket(item.path("isRocket").asBoolean())
                .isFreeShipping(item.path("isFreeShipping").asBoolean())
                .build();
    }
}
