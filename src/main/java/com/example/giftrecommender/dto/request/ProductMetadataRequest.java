package com.example.giftrecommender.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProductMetadataRequest(

        @Schema(description = "계층형 카테고리 경로", example = "패션의류>남성의류>재킷")
        String category,

        @Schema(description = "상품 의미 보완용 짧은 설명", example = "가벼운 착용감으로 데일리 러닝에 적합한 운동화")
        @Size(max = 255, message = "shortDescription은 최대 255자까지 가능합니다.")
        String shortDescription,

        @Schema(description = "키워드 목록(기존 구조 그대로 반영)", example = "[\"러닝화\",\"조깅화\",\"트레이닝화\"]")
        List<String> keywords
) {}
