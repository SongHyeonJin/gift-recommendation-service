package com.example.giftrecommender.common;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor(staticName = "add")
public class BasicResponseDto<T> {
    private final int statusCode;
    private final String message;
    private final T data;

    public static <T> BasicResponseDto<T> success(String message, T data) {
        return add(HttpStatus.OK.value(), message, data);
    }

    public static <T> BasicResponseDto<T> fail(String message) {
        return add(HttpStatus.BAD_REQUEST.value(), message, null);
    }

}
