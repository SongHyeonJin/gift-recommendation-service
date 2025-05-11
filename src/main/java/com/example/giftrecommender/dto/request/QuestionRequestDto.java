package com.example.giftrecommender.dto.request;

import com.example.giftrecommender.domain.enums.QuestionType;

public record QuestionRequestDto(
        String content,
        QuestionType type,
        int order
) {}
