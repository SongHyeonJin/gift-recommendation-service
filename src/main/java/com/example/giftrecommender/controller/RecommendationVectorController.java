package com.example.giftrecommender.controller;

import com.example.giftrecommender.common.BasicResponseDto;
import com.example.giftrecommender.dto.request.RecommendationRequestDto;
import com.example.giftrecommender.dto.response.CrawlingProductRecommendationResponseDto;
import com.example.giftrecommender.service.RecommendationVectorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@ConditionalOnProperty(prefix = "vector", name = "enabled", havingValue = "true")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/guests/{guestId}/recommendation-sessions/{sessionId}")
public class RecommendationVectorController {
    private final RecommendationVectorService recommendationVectorService;

    @Operation(summary = "선물 추천 요청", description = "대표 키워드와 가격 조건을 기반으로 상품을 추천받습니다. (시간당 호출 10회 제한)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "추천 성공",
                    content = @Content(schema = @Schema(implementation = CrawlingProductRecommendationResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "404", description = "게스트 또는 세션 없음")
    })
    @PostMapping("/recommendation/vector")
    public ResponseEntity<BasicResponseDto<CrawlingProductRecommendationResponseDto>> getRecommendationVector(
            @Parameter(
                    description = "게스트 ID",
                    example = "a31bbec5-1886-41c9-a079-9a375a6dfadb"
            )
            @PathVariable("guestId") UUID guestId,

            @Parameter(
                    description = "추천 세션 ID",
                    example = "9e3b4b95-7698-4feb-8c64-956c77e65bf8"
            )
            @PathVariable("sessionId") UUID sessionId,
            @RequestBody @Valid RecommendationRequestDto request
    ) {
        CrawlingProductRecommendationResponseDto response =
                recommendationVectorService.recommendByVector(guestId, sessionId, request);
        return ResponseEntity.ok(BasicResponseDto.success("추천 완료", response));
    }
}