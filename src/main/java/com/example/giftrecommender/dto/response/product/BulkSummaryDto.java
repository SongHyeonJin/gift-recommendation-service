package com.example.giftrecommender.dto.response.product;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "벌크 저장 결과 요약")
public record BulkSummaryDto(
        int total,
        int success,
        int duplicated,
        int failed
) {}
