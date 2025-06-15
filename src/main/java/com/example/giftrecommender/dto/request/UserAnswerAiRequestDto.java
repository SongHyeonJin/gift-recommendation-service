package com.example.giftrecommender.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "GPT 기반 질문과 선택지에 대한 유저 응답을 담는 요청 DTO (질문, 선택지 포함)", example = """
{
  "question": {
    "content": "연인의 취미가 뭐야?",
    "type": "CHOICE",
    "order": 4
  },
  "options": [
    {
      "content": "캠핑"
    },
    {
      "content": "운동"
    },
    {
      "content": "영화"
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
