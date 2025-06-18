package com.example.giftrecommender.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "GPT 기반 질문과 선택지에 대한 유저 응답을 담는 요청 DTO (질문, 선택지 포함)", example = """
{
  "question": {
    "content": "OO이는 어떤 느낌의 선물을 좋아해?",
    "type": "CHOICE",
    "order": 7
  },
  "options": [
    {
      "content": "실용적"
    },
    {
      "content": "감동적"
    },
    {
      "content": "가성비"
    }
  ],
  "selectedIndex": 1
}
""")
public record UserAnswerAiRequestDto(

        @Schema(description = "GPT가 생성한 질문 정보", required = true)
        QuestionRequestDto question,

        @Schema(description = "GPT가 생성한 선택지 목록", required = true)
        List<AnswerOptionRequestDto> options,

        @Schema(description = "사용자가 선택한 선택지의 인덱스", example = "1", required = true)
        int selectedIndex

) {}
