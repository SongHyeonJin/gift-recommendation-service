package com.example.giftrecommender.domain.enums;

import lombok.Getter;
import org.springframework.data.domain.Sort;

import java.util.EnumSet;

@Getter
public enum ProductSort {
    SCORE("score"),
    PRICE("price"),
    REVIEW_COUNT("reviewCount"),
    RATING("rating"),
    IS_CONFIRMED("isConfirmed"),
    CREATED_AT("createdAt"),
    UPDATED_AT("updatedAt");

    private final String field;

    ProductSort(String field) {
        this.field = field;
    }

    public static boolean isAllowed(String property) {
        for (ProductSort ps : EnumSet.allOf(ProductSort.class)) {
            if (ps.field.equals(property)) return true;
        }
        return false;
    }

    public static Sort defaultSort() {
        return Sort.by(Sort.Order.desc(CREATED_AT.field));
    }
}

