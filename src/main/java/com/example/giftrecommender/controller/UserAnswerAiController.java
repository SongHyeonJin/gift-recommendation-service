package com.example.giftrecommender.controller;

import com.example.giftrecommender.common.BasicResponseDto;
import com.example.giftrecommender.dto.request.UserAnswerAiRequestDto;
import com.example.giftrecommender.service.UserAnswerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "04-유저 응답 (AI 기반 생성된 질문, 선택지)", description = "GPT 기반 질문, 선택지에 대한 유저 응답 저장 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/guests/{guestId}/recommendation-sessions/{sessionId}")
public class UserAnswerAiController {

    private final UserAnswerService userAnswerService;

    @Operation(summary = "GPT 기반 질문, 선택지 저장", description = "4~6번 GPT 기반 질문과 선택지를 저장합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "저장 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "404", description = "게스트 또는 세션 없음"),
            @ApiResponse(responseCode = "500", description = "서버 에러")
    })
    @PostMapping("/ai-answers")
    public ResponseEntity<BasicResponseDto<Void>> saveAiQuestion(
            @PathVariable("guestId") UUID guestId,
            @PathVariable("sessionId") UUID sessionId,
            @RequestBody UserAnswerAiRequestDto requestDto) {
        userAnswerService.saveAiQuestionAndAnswer(guestId, sessionId, requestDto);
        return ResponseEntity.ok(BasicResponseDto.success("AI 질문, 선택지 저장 완료 및 유저 응답 완료", null));
    }
}
