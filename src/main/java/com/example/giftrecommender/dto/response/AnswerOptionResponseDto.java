package com.example.giftrecommender.dto.response;


import io.swagger.v3.oas.annotations.media.Schema;

public record AnswerOptionResponseDto (

        @Schema(description = "선택지 ID", example = "1")
        Long id,

        @Schema(description = "선택지 내용", example = "여자친구")
        String content

) {}
