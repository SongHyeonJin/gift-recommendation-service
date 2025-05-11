package com.example.giftrecommender.dto.request;

import com.example.giftrecommender.domain.enums.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserAnswerRequestDto (
        @Schema(description = "질문 ID", example = "1")
        Long questionId,

        @Schema(description = "질문 타입 (CHOICE)", example = "CHOICE")
        QuestionType type,

        @Schema(description = "선택한 답변 옵션 ID", example = "1")
        Long answerOptionId
) {}
