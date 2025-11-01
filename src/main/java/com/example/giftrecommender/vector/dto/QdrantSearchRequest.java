package com.example.giftrecommender.vector.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.Map;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QdrantSearchRequest {
    @JsonProperty("vector")
    private float[] vector;

    @JsonProperty("limit")
    private int limit;

    @JsonProperty("with_payload")
    private boolean withPayload;

    @JsonProperty("with_vectors")
    private boolean withVectors;

    @JsonProperty("filter")
    private Map<String, Object> filter;

    @JsonProperty("score_threshold")
    private Double scoreThreshold;

    public QdrantSearchRequest(float[] vector,
                               int limit,
                               boolean withPayload,
                               boolean withVectors,
                               Map<String, Object> filter,
                               Double scoreThreshold) {
        this.vector = vector;
        this.limit = limit;
        this.withPayload = withPayload;
        this.withVectors = withVectors;
        this.filter = filter;
        this.scoreThreshold = scoreThreshold;
    }
}
