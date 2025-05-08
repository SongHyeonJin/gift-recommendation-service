package com.example.giftrecommender.common.exception;

import com.example.giftrecommender.common.BasicResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ErrorException.class)
    public ResponseEntity<BasicResponseDto<Void>> handleErrorException(ErrorException e) {
        return ResponseEntity.status(e.getExceptionEnum().getStatusCode())
                .body(BasicResponseDto.fail(e.getExceptionEnum().getMessage()));
    }

}
