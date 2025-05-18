package com.example.giftrecommender.controller;

import com.example.giftrecommender.common.BasicResponseDto;
import com.example.giftrecommender.dto.request.RecommendationRequestDto;
import com.example.giftrecommender.dto.response.RecommendationResponseDto;
import com.example.giftrecommender.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "06-추천 API", description = "키워드 기반 선물 추천 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/guests/{guestId}/recommendation-sessions/{sessionId}")
public class RecommendationController {

    private final RecommendationService recommendationService;

    @Operation(summary = "선물 추천 요청", description = "대표 키워드와 가격 조건을 기반으로 상품을 추천받습니다. (시간당 호출 10회 제한)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "추천 성공",
                    content = @Content(schema = @Schema(implementation = RecommendationResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "404", description = "게스트 또는 세션 없음")
    })
    @PostMapping("/recommendation")
    public ResponseEntity<BasicResponseDto<RecommendationResponseDto>> getRecommendation(
            @PathVariable("guestId") UUID guestId,
            @PathVariable("sessionId") UUID sessionId,

            @RequestBody RecommendationRequestDto request
    ) {
        RecommendationResponseDto response = recommendationService.recommend(guestId, sessionId, request.keywords());
        return ResponseEntity.ok(BasicResponseDto.success("추천 완료", response));
    }

    @Operation(
            summary = "추천 결과 조회",
            description = "추천 세션 ID(sessionId)를 기반으로 추천 결과를 조회합니다. 추천 생성 이후 다시 확인하거나 재접속 시 사용합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "추천 결과 조회 성공",
                    content = @Content(schema = @Schema(implementation = RecommendationResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "추천 결과 없음"),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류")
    })
    @GetMapping("/recommendation")
    public ResponseEntity<BasicResponseDto<RecommendationResponseDto>> getRecommendationResult(
            @PathVariable("guestId") UUID guestId,
            @PathVariable("sessionId") UUID sessionId
    ) {
        RecommendationResponseDto response = recommendationService.getRecommendationResult(guestId, sessionId);
        return ResponseEntity.ok(BasicResponseDto.success("추천 결과 조회 성공", response));
    }

}
