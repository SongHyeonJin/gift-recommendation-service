package com.example.giftrecommender.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ExceptionEnum {
    GUEST_NOT_FOUND(HttpStatus.NOT_FOUND.value(), "해당 게스트를 찾을 수 없습니다."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND.value(), "추천 세션을 찾을 수 없습니다."),
    QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND.value(), "질문을 찾을 수 없습니다."),
    OPTION_NOT_FOUND(HttpStatus.NOT_FOUND.value(), "선택지를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR.value(), "서버 내부 오류가 발생했습니다.");


    private final int statusCode;
    private final String message;

    ExceptionEnum(int statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }
}
