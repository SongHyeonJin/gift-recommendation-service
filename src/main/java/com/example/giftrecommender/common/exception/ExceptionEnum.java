package com.example.giftrecommender.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ExceptionEnum {
    GUEST_NOT_FOUND(HttpStatus.NOT_FOUND.value(), "해당 게스트를 찾을 수 없습니다."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND.value(), "추천 세션을 찾을 수 없습니다."),
    QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND.value(), "질문을 찾을 수 없습니다."),
    OPTION_NOT_FOUND(HttpStatus.NOT_FOUND.value(), "선택지를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR.value(), "서버 내부 오류가 발생했습니다."),
    INVALID_AI_ANSWER_INDEX(HttpStatus.BAD_REQUEST.value(), "선택된 AI 답변 인덱스가 유효하지 않습니다."),
    FORBIDDEN( HttpStatus.FORBIDDEN.value(),"접근 권한이 없습니다."),
    NO_PRODUCT_MATCH(HttpStatus.BAD_REQUEST.value(), "추천 조건에 맞는 상품이 없습니다."),
    RESULT_NOT_FOUND(HttpStatus.BAD_REQUEST.value(), "추천 결과를 찾을 수 없습니다."),
    SESSION_FORBIDDEN(HttpStatus.FORBIDDEN.value(), "세션 접근 권한이 없습니다."),
    RECOMMENDATION_EMPTY(HttpStatus.BAD_REQUEST.value(), "추천 결과가 없습니다. 키워드를 조정하거나 다시 시도해주세요."),
    QUOTA_SECOND_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS.value(), "잠시 후 다시 시도해주세요. (초당 호출 제한)"),
    QUOTA_DAILY_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS.value(), "오늘은 더 이상 호출할 수 없습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST.value(), "요청 파라미터가 올바르지 않습니다."),
    PRODUCT_NOT_FOUND(HttpStatus.BAD_REQUEST.value(), "상품을 찾을 수 없습니다."),;


    private final int statusCode;
    private final String message;

    ExceptionEnum(int statusCode, String message) {
        this.statusCode = statusCode;
        this.message = message;
    }
}
