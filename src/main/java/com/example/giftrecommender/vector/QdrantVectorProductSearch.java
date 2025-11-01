package com.example.giftrecommender.vector;

import com.example.giftrecommender.config.QdrantProps;
import com.example.giftrecommender.vector.dto.QdrantSearchRequest;
import com.example.giftrecommender.vector.dto.QdrantSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "vector", name = "enabled", havingValue = "true")
public class QdrantVectorProductSearch implements VectorProductSearch {

    private final WebClient qdrantWebClient;
    private final EmbeddingService embeddingService;
    private final QdrantProps qdrantProps;

    @Override
    public List<ScoredId> searchWithScores(String query,
                                           int minPrice, int maxPrice,
                                           String age, String gender,
                                           int topK, double threshold) {
        // 1. 임베딩
        List<Float> embedded;
        try {
            embedded = embeddingService.embed(query);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        float[] vector = toFloatArray(embedded);

        // 2. 필터 구성 (가격만 유지. 필요 시 확장)
        Map<String, Object> filter = null;
        if (minPrice > 0 || maxPrice > 0) {
            List<Map<String, Object>> must = new ArrayList<>();
            Map<String, Object> priceClause = new HashMap<>();
            Map<String, Object> range = new HashMap<>();
            if (minPrice > 0) range.put("gte", minPrice);
            if (maxPrice > 0) range.put("lte", maxPrice);
            priceClause.put("key", "price");
            priceClause.put("range", range);
            must.add(priceClause);
            filter = Collections.singletonMap("must", must);
        }

        // 3. 요청 바디 (score_threshold를 절대 보내지 않음)
        int limit = Math.max(topK, 50);
        QdrantSearchRequest requestBody = new QdrantSearchRequest(
                vector, limit, true, false, filter, null // score_threshold=null
        );

        try {
            QdrantSearchResponse response = qdrantWebClient.post()
                    .uri("/collections/{c}/points/search", qdrantProps.getCollection())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r -> r.bodyToMono(String.class).map(msg ->
                            new RuntimeException("Qdrant search error " + r.statusCode() + ": " + msg)))
                    .bodyToMono(QdrantSearchResponse.class)
                    .block();

            if (response == null || response.getResult() == null) {
                return Collections.emptyList();
            }

            // 4. productId 추출(유사도 순, 중복 제거)
            LinkedHashMap<Long, Double> ordered = new LinkedHashMap<>();
            for (QdrantSearchResponse.Item item : response.getResult()) {
                Map<String, Object> payload = item.getPayload();
                if (payload == null) continue;

                Object pidObj = payload.get("productId");
                Long pid = null;
                if (pidObj instanceof Number) {
                    pid = ((Number) pidObj).longValue();
                } else if (pidObj instanceof String s && s.matches("\\d+")) {
                    pid = Long.parseLong(s);
                }
                if (pid != null && !ordered.containsKey(pid)) {
                    ordered.put(pid, item.getScore());
                }
            }

            return ordered.entrySet().stream()
                    .limit(topK)
                    .map(e -> new ScoredId(e.getKey(), e.getValue()))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("[QDRANT][SEARCH][FAIL] q='{}' err={}", query, e.toString());
            return Collections.emptyList();
        }
    }

    private static float[] toFloatArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }
}
