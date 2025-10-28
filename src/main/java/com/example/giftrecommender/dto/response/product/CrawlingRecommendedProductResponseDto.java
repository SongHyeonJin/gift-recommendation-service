package com.example.giftrecommender.dto.response.product;

import com.example.giftrecommender.domain.entity.CrawlingProduct;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@Schema(description = "크롤링된 상품 추천 응답 DTO")
public record CrawlingRecommendedProductResponseDto(

        @Schema(description = "상품 ID", example = "1")
        long id,

        @Schema(description = "사용자에게 표시할 상품명", example = "USB 멀티탭 3구")
        String displayName,

        @Schema(description = "가격(원 단위)", example = "15900")
        int price,

        @Schema(description = "상품 상세 페이지 링크", example = "https://store.example.com/products/12345")
        String productUrl,

        @Schema(description = "대표 이미지 URL", example = "https://image.example.com/item.jpg")
        String imageUrl,

        @Schema(description = "플랫폼명 (예: 텐바이텐, 네이버)", example = "네이버")
        String platform,

        @Schema(description = "검색/추천용 키워드", example = "[\"무드등\", \"조명\", \"분위기\"]")
        List<String> keywords,

        @Schema(description = "리뷰 개수", example = "312")
        int reviewCount,

        @Schema(description = "별점 (0.0 ~ 5.0)", example = "4.3")
        BigDecimal rating,

        @Schema(description = "판매자명", example = "무아스")
        String sellerName,

        @Schema(description = "광고 여부", example = "false")
        boolean isAdvertised
) {
    /**
     * CrawlingProduct 엔티티 → DTO 변환 메서드
     */
    public static CrawlingRecommendedProductResponseDto from(CrawlingProduct product) {
        int price = product.getPrice() == null ? 0 : product.getPrice();
        int reviewCount = product.getReviewCount() == null ? 0 : product.getReviewCount();
        BigDecimal rating = product.getRating() == null ? new BigDecimal("0.0") : product.getRating();
        List<String> keywords = product.getKeywords() == null ? Collections.emptyList() : product.getKeywords();
        boolean advertised = Boolean.TRUE.equals(product.getIsAdvertised());

        return new CrawlingRecommendedProductResponseDto(
                product.getId(),
                product.getDisplayName(),
                price,
                product.getProductUrl(),
                product.getImageUrl(),
                product.getPlatform(),
                keywords,
                reviewCount,
                rating,
                product.getSellerName(),
                advertised
        );
    }

    /**
     * CrawlingProduct 리스트 → DTO 리스트 변환 메서드
     */
    public static List<CrawlingRecommendedProductResponseDto> fromList(List<CrawlingProduct> products) {
        return products.stream()
                .map(CrawlingRecommendedProductResponseDto::from)
                .toList();
    }
}

