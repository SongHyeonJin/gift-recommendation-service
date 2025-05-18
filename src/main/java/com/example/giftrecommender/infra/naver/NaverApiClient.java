package com.example.giftrecommender.infra.naver;

import com.example.giftrecommender.common.exception.ErrorException;
import com.example.giftrecommender.common.exception.ExceptionEnum;
import com.example.giftrecommender.common.quota.RedisQuotaManager;
import com.example.giftrecommender.dto.response.ProductResponseDto;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class NaverApiClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final RedisQuotaManager quotaManager;

    @Value("${naver.client-id}")
    private String clientId;

    @Value("${naver.client-secret}")
    private String clientSecret;

    public List<ProductResponseDto> search(String query, int page, int display) {
        if (!quotaManager.canCall()) {
            throw new ErrorException(ExceptionEnum.QUOTA_EXCEEDED);
        }

        int start = (page - 1) * display + 1;
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://openapi.naver.com/v1/search/shop.json?query=" + encodedQuery +
                "&display=" + display + "&start=" + start;

        log.info("네이버 쇼핑 API 요청 시작: query='{}'", query);
        log.debug("요청 URL: {}", url);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Naver-Client-Id", clientId);
        headers.set("X-Naver-Client-Secret", clientSecret);

        HttpEntity<?> entity = new HttpEntity<>(headers);

        ResponseEntity<JsonNode> response = restTemplate.exchange(
                URI.create(url), HttpMethod.GET, entity, JsonNode.class
        );
        log.info("네이버 응답 상태 코드: {}", response.getStatusCode());

        JsonNode body = response.getBody();
        log.debug("네이버 응답 바디: {}", body);

        JsonNode items = body.get("items");

        if (items == null || !items.isArray()) {
            log.warn("items 노드가 없거나 배열이 아님. 전체 응답: {}", body);
            return List.of();
        }

        List<ProductResponseDto> result = new ArrayList<>();
        for (JsonNode item : items) {
            result.add(new ProductResponseDto(
                    null,
                    item.get("title").asText(),
                    item.get("link").asText(),
                    item.get("image").asText(),
                    item.get("lprice").asInt(),
                    item.get("mallName").asText()
            ));
        }

        log.info("파싱된 상품 수: {}", result.size());
        return result;
    }
}

