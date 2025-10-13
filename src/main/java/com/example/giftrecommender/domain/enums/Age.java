package com.example.giftrecommender.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Age {
    KID, TEEN, YOUNG_ADULT, SENIOR, NONE;

    @JsonCreator
    public static Age from(String value) {
        if (value == null) return NONE;
        return Age.valueOf(value.trim().toUpperCase());
    }
}
