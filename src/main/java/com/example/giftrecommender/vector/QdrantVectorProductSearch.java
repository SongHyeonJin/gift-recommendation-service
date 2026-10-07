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
    public List<ScoredId> searchWithScores(
            String query,
            Integer minPrice, Integer maxPrice,
            String age, String gender,
            int topK, Double threshold
    ) {
        List<Float> embedded;
        try {
            embedded = embeddingService.embed(query);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        float[] vector = toFloatArray(embedded);

        // ✅ null-safe 보정
        int min = (minPrice == null) ? 0 : minPrice;
        int max = (maxPrice == null) ? Integer.MAX_VALUE : maxPrice;

        // ✅ Qdrant filter 구성
        Map<String, Object> filter = buildFilter(min, max, age, gender);

        // Qdrant limit (search 결과 후보 수)
        int limit = Math.max(topK, 50);

        QdrantSearchRequest requestBody = new QdrantSearchRequest(
                vector,
                limit,
                true,          // with_vector
                false,         // with_payload
                filter,
                null           // score_threshold (여기서는 사용 안 함)
        );

        try {
            log.debug("[QDRANT][SEARCH][CALL] q='{}', limit={}, price=[{},{}], age={}, gender={}, threshold={}",
                    query, limit, min, max, age, gender, threshold);

            QdrantSearchResponse response = qdrantWebClient.post()
                    .uri("/collections/{c}/points/search", qdrantProps.getCollection())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r -> r.bodyToMono(String.class).map(msg ->
                            new RuntimeException("Qdrant search error " + r.statusCode() + ": " + msg)))
                    .bodyToMono(QdrantSearchResponse.class)
                    .block();

            if (response == null || response.getResult() == null || response.getResult().isEmpty()) {
                log.debug("[QDRANT][SEARCH][OK] q='{}', rawHits=0", query);
                return Collections.emptyList();
            }

            boolean applyThreshold = (threshold != null);
            double th = applyThreshold ? threshold : Double.NEGATIVE_INFINITY;

            LinkedHashMap<Long, Double> ordered = new LinkedHashMap<>();

            for (QdrantSearchResponse.Item item : response.getResult()) {
                Map<String, Object> payload = item.getPayload();
                if (payload == null) continue;

                Long pid = extractProductId(payload.get("productId"));
                if (pid == null || ordered.containsKey(pid)) continue;

                // score는 distance (cosine metric 가정)
                double distance = item.getScore();
                double similarity = 1.0 - distance;

                if (applyThreshold && similarity < th) continue;

                ordered.put(pid, similarity);
            }

            log.debug("[QDRANT][SEARCH][OK] q='{}', hits={} (after threshold={})",
                    query, ordered.size(), threshold);

            return ordered.entrySet().stream()
                    .limit(topK)
                    .map(e -> new ScoredId(e.getKey(), e.getValue()))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("[QDRANT][SEARCH][FAIL] q='{}' err={}", query, e.toString(), e);
            return Collections.emptyList();
        }
    }

    private Map<String, Object> buildFilter(int minPrice, int maxPrice, String age, String gender) {
        List<Map<String, Object>> must = new ArrayList<>();

        // ✅ price range (의미: min/max가 유효할 때만)
        // - min=0, max=Integer.MAX_VALUE면 사실상 전체라 필터를 안 거는게 낫다.
        boolean hasMin = minPrice > 0;
        boolean hasMax = maxPrice < Integer.MAX_VALUE;

        if (hasMin || hasMax) {
            Map<String, Object> range = new HashMap<>();
            if (hasMin) range.put("gte", minPrice);
            if (hasMax) range.put("lte", maxPrice);

            Map<String, Object> priceClause = new HashMap<>();
            priceClause.put("key", "price");
            priceClause.put("range", range);

            must.add(priceClause);
        }

        // ✅ age match
        if (age != null && !age.isBlank()) {
            Map<String, Object> ageClause = new HashMap<>();
            ageClause.put("key", "age");
            ageClause.put("match", Map.of("value", age));
            must.add(ageClause);
        }

        // ✅ gender match
        if (gender != null && !gender.isBlank()) {
            Map<String, Object> genderClause = new HashMap<>();
            genderClause.put("key", "gender");
            genderClause.put("match", Map.of("value", gender));
            must.add(genderClause);
        }

        if (must.isEmpty()) return null;
        return Map.of("must", must);
    }

    private Long extractProductId(Object pidObj) {
        if (pidObj instanceof Number n) return n.longValue();
        if (pidObj instanceof String s && s.matches("\\d+")) return Long.parseLong(s);
        return null;
    }



    private static float[] toFloatArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

}
