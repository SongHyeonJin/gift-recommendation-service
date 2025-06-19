package com.example.giftrecommender.dto.response;

import com.example.giftrecommender.domain.enums.QuestionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "질문 응답 DTO", example = """
{
  "id": 1,
  "content": "누구한테 줄거야?",
  "type": "CHOICE",
  "order": 1,
  "options": [
    { "content": "친구에게!" },
    { "content": "연인에게" },
    { "content": "부모님께" },
    { "content": "형제/자매에게!" },
    { "content": "친척에게!" },
    { "content": "지인에게!" }
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
