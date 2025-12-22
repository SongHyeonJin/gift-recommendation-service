package com.example.giftrecommender.dto.request.product;

import com.example.giftrecommender.domain.enums.Age;
import com.example.giftrecommender.domain.enums.Gender;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record CrawlingProductRequestDto(

        @Schema(description = "원본 상품명", example = "[무아스] 마카롱 3구 USB 고용량 큐브 멀티탭 1.5m")
        @NotBlank
        String originalName,

        @Schema(description = "노출 상품명", example = "마카롱 3구 USB 고용량 큐브 멀티탭 1.5m")
        String displayName,

        @Schema(description = "상품 의미 보완용 짧은 설명", example = "USB 포트를 포함한 컴팩트한 멀티탭")
        @Size(max = 255)
        String shortDescription,

        @Schema(description = "가격 (원 단위)", example = "15900")
        @NotNull
        @PositiveOrZero
        Integer price,

        @Schema(description = "대표 이미지 URL", example = "https://image.10x10.co.kr/image1.jpg")
        @NotBlank
        String imageUrl,

        @Schema(description = "상품 상세 페이지 URL", example = "https://10x10.co.kr/item/6625336")
        @NotBlank
        String productUrl,

        @Schema(description = "카테고리명", example = "디지털/PC")
        String category,

        @Schema(description = "검색/추천용 키워드 배열", example = "[\"USB\", \"멀티탭\", \"케이블\"]")
        List<String> keywords,

        @Schema(description = "리뷰 개수", example = "128")
        Integer reviewCount,

        @Schema(description = "별점 (0~5)", example = "4.8")
        @DecimalMin(value = "0.0")
        @DecimalMax(value = "5.0")
        BigDecimal rating,

        @Schema(description = "판매자명", example = "무무샵")
        String sellerName,

        @Schema(description = "플랫폼명", example = "텐바이텐")
        String platform,

        @Schema(description = "추천 타겟 성별", example = "FEMALE")
        Gender gender,

        @Schema(description = "추천 타겟 나이", example = "YOUNG_ADULT")
        Age age,

        @Schema(description = "쿠팡 광고 여부", example = "false")
        Boolean isAdvertised

) {}
