package com.example.giftrecommender.dto.response.product;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "크롤링 상품 벌크 저장 최상위 응답")
public record CrawlingProductBulkSaveResponseDto(
        BulkSummaryDto summary,
        List<BulkItemResultDto> results
) {}
