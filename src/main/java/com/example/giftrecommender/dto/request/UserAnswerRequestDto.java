package com.example.giftrecommender.dto.request;

import com.example.giftrecommender.domain.enums.AnswerOptionType;
import com.example.giftrecommender.domain.enums.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserAnswerRequestDto(
        @Schema(description = "질문 ID", example = "1")
        Long questionId,

        @Schema(description = "질문 출처 유형 (FIXED)", example = "FIXED")
        QuestionType questionType,

        @Schema(description = "답변 방식 (CHOICE 또는 TEXT)", example = "CHOICE")
        AnswerOptionType answerOptionType,

        @Schema(description = "선택한 답변 옵션 ID (선택형일 경우 필수)", example = "1")
        Long answerOptionId,

        @Schema(description = "직접 입력한 텍스트 (TEXT 또는 기타일 경우)", example = "보드게임")
        String answerText
) {}
