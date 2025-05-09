package com.example.giftrecommender.dto.response;

import com.example.giftrecommender.domain.enums.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "질문 응답 DTO", example = """
{
  "id": 1,
  "content": "누구에게 선물할 건가요?",
  "type": "CHOICE",
  "order": 1,
  "options": [
    { "content": "연인", "recommendationKeyword": "연인" },
    { "content": "부모님", "recommendationKeyword": "부모님" },
    { "content": "선생님", "recommendationKeyword": "선생님" },
    { "content": "썸 관계", "recommendationKeyword": "썸" },
    { "content": "친구", "recommendationKeyword": "친구" }
  ]
}
""")
public record QuestionResponseDto(
        @Schema(description = "질문 ID", example = "1")
        Long id,

        @Schema(description = "질문 내용", example = "누구에게 선물하나요?")
        String content,

        @Schema(description = "질문 타입", example = "CHOICE")
        QuestionType type,

        @Schema(description = "질문 순서", example = "1")
        Integer order,

        @Schema(description = "질문 보기 목록")
        List<AnswerOptionResponseDto> options
) {}
