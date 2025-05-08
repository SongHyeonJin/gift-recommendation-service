package com.example.giftrecommender.common.exception;

import lombok.Getter;

@Getter
public class ErrorException extends RuntimeException {

    private final ExceptionEnum exceptionEnum;


    public ErrorException(ExceptionEnum exceptionEnum) {
        super(exceptionEnum.getMessage());
        this.exceptionEnum = exceptionEnum;
    }
}
