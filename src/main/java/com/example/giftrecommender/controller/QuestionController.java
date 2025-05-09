package com.example.giftrecommender.controller;

import com.example.giftrecommender.common.BasicResponseDto;
import com.example.giftrecommender.dto.response.QuestionResponseDto;
import com.example.giftrecommender.service.QuestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "05-질문 (선택지 포함)", description = "선물 추천 질문 목록 관련 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionService questionService;

    @Operation(summary = "질문 목록 조회", description = "질문 리스트와 선택지를 조회합니다")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "질문 목록 조회 성공"),
            @ApiResponse(responseCode = "500", description = "서서버 내부 오류")
    })
    @GetMapping
    public ResponseEntity<BasicResponseDto<List<QuestionResponseDto>>> getQuestions() {
        return ResponseEntity.ok(BasicResponseDto.success("질문 목록 조회 성공", questionService.getAllQuestion()));
    }

}
