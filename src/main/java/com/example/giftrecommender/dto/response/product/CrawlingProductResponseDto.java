package com.example.giftrecommender.dto.response.product;

import com.example.giftrecommender.domain.entity.CrawlingProduct;
import com.example.giftrecommender.domain.enums.Age;
import com.example.giftrecommender.domain.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record CrawlingProductResponseDto(

        @Schema(description = "상품 ID", example = "1")
        Long id,

        @Schema(description = "원본 상품명", example = "[무아스] 마카롱 3구 USB 고용량 큐브 멀티탭 1.5m")
        String originalName,

        @Schema(description = "사용자에게 표시할 상품명", example = "USB 멀티탭 3구")
        String displayName,

        @Schema(description = "상품 의미 보완용 짧은 설명", example = "USB 포트를 포함한 컴팩트한 멀티탭")
        String shortDescription,

        @Schema(description = "가격 (원 단위)", example = "15900")
        Integer price,

        @Schema(description = "대표 이미지 URL", example = "https://image.10x10.co.kr/image1.jpg")
        String imageUrl,

        @Schema(description = "상품 상세 페이지 URL", example = "https://10x10.co.kr/item/6625336")
        String productUrl,

        @Schema(description = "카테고리명", example = "디지털/PC")
        String category,

        @Schema(description = "검색/추천용 키워드 배열", example = "[\"USB\", \"멀티탭\", \"케이블\"]")
        List<String> keywords,

        @Schema(description = "리뷰 개수", example = "128")
        Integer reviewCount,

        @Schema(description = "별점 (0~5)", example = "4.8")
        BigDecimal rating,

        @Schema(description = "판매자명", example = "무무샵")
        String sellerName,

        @Schema(description = "플랫폼명", example = "텐바이텐")
        String platform,

        @Schema(description = "자동점수 + 수동점수 총합", example = "12")
        Integer score,

        @Schema(description = "관리자 수동 점수 부여 여부", example = "false")
        Boolean adminCheck,

        @Schema(description = "성별 태그", example = "female")
        Gender gender,

        @Schema(description = "연령대 태그", example = "young_adult")
        Age age,

        @Schema(description = "관리자 컨펌 여부", example = "false")
        Boolean isConfirmed,

        @Schema(description = "쿠팡 광고 여부", example = "false")
        Boolean isAdvertised,

        @Schema(description = "생성 시각", example = "2025-08-07T12:00:00")
        LocalDateTime createdAt,

        @Schema(description = "수정 시각", example = "2025-08-07T12:10:00")
        LocalDateTime updatedAt

) {

        public static CrawlingProductResponseDto from(CrawlingProduct product) {
                return new CrawlingProductResponseDto(
                        product.getId(),
                        product.getOriginalName(),
                        product.getDisplayName(),
                        product.getShortDescription(),
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
                        product.getIsAdvertised(),
                        product.getCreatedAt(),
                        product.getUpdatedAt()
                );
        }
}
