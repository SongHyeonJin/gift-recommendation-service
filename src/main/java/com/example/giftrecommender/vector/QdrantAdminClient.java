package com.example.giftrecommender.vector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

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

    public void setKeywordsForPoint(Long pointId, List<String> keywords) {
        Map<String, Object> body = Map.of(
                "payload", Map.of("keywords", keywords),
                "points", List.of(pointId)
        );

        qdrantWebClient.post()
                .uri("/collections/{c}/points/payload", collection)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess(res -> log.info("[QDRANT][SET-PAYLOAD] pointId={} size={}", pointId, keywords.size()))
                .doOnError(err -> log.error("[QDRANT][SET-PAYLOAD][FAIL] pointId={} msg={}", pointId, err.getMessage()))
                .block();
    }
}
