package com.example.giftrecommender.mapper;

import com.example.giftrecommender.domain.entity.CrawlingProduct;
import com.example.giftrecommender.dto.response.CrawlingProductResponseDto;

public class CrawlingProductMapper {

    public static CrawlingProductResponseDto toDto(CrawlingProduct product) {
        return new CrawlingProductResponseDto(
                product.getId(),
                product.getOriginalName(),
                product.getDisplayName(),
                product.getPrice(),
                product.getImageUrl(),
                product.getProductUrl(),
                product.getCategory(),
                product.getKeywords(),
                product.getReviewCount(),
                product.getRating(),
                product.getSellerName(),
                product.getPlatform(),
                product.getScore(),
                product.getAdminCheck(),
                product.getGender(),
                product.getAge(),
                product.getIsConfirmed(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}