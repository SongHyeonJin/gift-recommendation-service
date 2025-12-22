package com.example.giftrecommender.vector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "vector", name = "enabled", havingValue = "true")
public class QdrantAdminClient {

    private final WebClient qdrantWebClient;

    @Value("${qdrant.collection}")
    private String collection;

    /**
     * Qdrant payload 갱신
     * - keywords
     * - category
     * - shortDescription
     */
    public void setPayloadForPoint(
            Long pointId,
            List<String> keywords,
            String category,
            String shortDescription
    ) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("keywords", keywords);
        payload.put("category", category);

        if (shortDescription != null && !shortDescription.isBlank()) {
            payload.put("shortDescription", shortDescription);
        }

        Map<String, Object> body = Map.of(
                "payload", payload,
                "points", List.of(pointId)
        );

        qdrantWebClient.post()
                .uri("/collections/{c}/points/payload", collection)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(res ->
                        log.info(
                                "[QDRANT][SET-PAYLOAD] pointId={} kw={} cat={} shortDesc={}",
                                pointId,
                                keywords == null ? 0 : keywords.size(),
                                category,
                                shortDescription != null && !shortDescription.isBlank()
                        )
                )
                .doOnError(err ->
                        log.error(
                                "[QDRANT][SET-PAYLOAD][FAIL] pointId={} msg={}",
                                pointId,
                                err.getMessage(),
                                err
                        )
                )
                .block();
    }
}
