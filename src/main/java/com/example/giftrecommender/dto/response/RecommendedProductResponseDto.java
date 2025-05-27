package com.example.giftrecommender.dto.response;

import com.example.giftrecommender.domain.entity.Product;
import com.example.giftrecommender.domain.entity.keyword.KeywordGroup;

import java.util.List;
import java.util.UUID;

public record RecommendedProductResponseDto(
        UUID publicId,
        String title,
        int price,
        String link,
        String imageUrl,
        String mallName,
        List<String> keywords
) {
    public static RecommendedProductResponseDto from(Product product) {
        List<String> keywordTags = product.getKeywordGroups().stream()
                .map(KeywordGroup::getMainKeyword)
                .toList();

        return new RecommendedProductResponseDto(
                product.getPublicId(),
                product.getTitle(),
                product.getPrice(),
                product.getLink(),
                product.getImageUrl(),
                product.getMallName(),
                keywordTags
        );
    }
}
