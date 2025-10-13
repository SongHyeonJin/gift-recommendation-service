package com.example.giftrecommender.domain.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Gender {
    MALE, FEMALE, ANY;

    @JsonCreator
    public static Gender from(String value) {
        if (value == null) return ANY;
        return Gender.valueOf(value.trim().toUpperCase());
    }
}
