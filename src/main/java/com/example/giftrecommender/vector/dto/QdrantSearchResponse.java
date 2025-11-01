package com.example.giftrecommender.vector.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QdrantSearchResponse {

    private List<Item> result;

    public void setResult(List<Item> result) { this.result = result; }

    @Getter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Item {
        private double score;
        private Map<String, Object> payload;

        public void setScore(double score) { this.score = score; }

        public void setPayload(Map<String, Object> payload) { this.payload = payload; }
    }
}
