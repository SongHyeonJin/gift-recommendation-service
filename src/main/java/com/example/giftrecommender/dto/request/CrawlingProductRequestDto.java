package com.example.giftrecommender.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public record CrawlingProductRequestDto(

        @Schema(description = "원본 상품명", example = "[무아스] 마카롱 3구 USB 고용량 큐브 멀티탭 1.5m")
        @NotBlank
        String originalName,

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
        String platform

) {}

/*
여러건 등록 예시
[
  {
    "originalName": "무아스 큐브 멀티탭",
    "price": 29000,
    "imageUrl": "https://example.com/image.jpg",
    "productUrl": "https://example.com/product",
    "category": "디자인문구",
    "keywords": ["멀티탭", "usb", "귀여움"],
    "reviewCount": 150,
    "rating": 4.5,
    "sellerName": "무아스",
    "platform": "텐바이텐"
  },
  {
    "originalName": "스누피 무드등",
    "price": 19900,
    "imageUrl": "https://example.com/snoopy.jpg",
    "productUrl": "https://example.com/snoopy",
    "category": "무드등",
    "keywords": ["조명", "선물", "귀여움"],
    "reviewCount": 80,
    "rating": 4.2,
    "sellerName": "라이팅샵",
    "platform": "네이버"
  }
]

 */