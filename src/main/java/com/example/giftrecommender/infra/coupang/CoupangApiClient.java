package com.example.giftrecommender.infra.coupang;

import com.example.giftrecommender.infra.coupang.config.CoupangApiConfig;
import com.example.giftrecommender.infra.coupang.dto.CoupangProductResponseDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
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
public class CoupangApiClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CoupangApiConfig config;

    public List<CoupangProductResponseDto> searchProducts(String keyword, int page) {
        try {
            // keyword를 인코딩
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String rawQuery = "keyword=" + encodedKeyword + "&page=" + page;

            // 요청 경로 및 전체 URL 구성
            String method = "GET";
            String path = "/v2/providers/affiliate_open_api/apis/openapi/v1/products/search";
            String uriWithQuery = path + "?" + rawQuery;
            String fullUrl = "https://api-gateway.coupang.com" + uriWithQuery;

            // HMAC 서명용 timestamp 생성 (UTC/GMT 기준)
            String timestamp = CoupangAuthUtil.generateTimestamp();

            // 쿠팡 API용 Authorization 헤더 생성 (RFC2104 HMAC-SHA256 방식)
            String authorization = CoupangAuthUtil.generateAuthorization(
                    method, path, rawQuery, config.getSecretKey(), config.getAccessKey(), timestamp
            );

            // Header 구성
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", authorization);
            headers.set("X-Timestamp", timestamp);
            headers.setContentType(MediaType.APPLICATION_JSON);

            log.info("[쿠팡 API 호출 정보]");
            log.info("keyword: {}", keyword);
            log.info("rawQuery: {}", rawQuery);
//            log.info("authorization: {}", authorization);

            // URI 객체로 직접 생성해서 RestTemplate 전달 → 자동 인코딩 방지
            ResponseEntity<String> response = restTemplate.exchange(
                    new URI(fullUrl), HttpMethod.GET, new HttpEntity<>(headers), String.class
            );

            if (response.getStatusCode() == HttpStatus.OK) {
                return productsResponseDto(response.getBody());
            } else {
                log.warn("❌ 쿠팡 API 실패 - Status: {}", response.getStatusCode());
                log.warn("Body: {}", response.getBody());
                return List.of();
            }
        } catch (Exception e) {
            log.error("🚨 쿠팡 API 호출 중 예외 발생", e);
            return List.of();
        }
    }

    /**
     * 응답 JSON에서 productData 파싱
     */
    private List<CoupangProductResponseDto> productsResponseDto(String body) throws Exception {
        log.info("쿠팡 API 응답 바디: {}", body);

        List<CoupangProductResponseDto> responseDto = new ArrayList<>();

        JsonNode root = objectMapper.readTree(body);
        String code = root.path("code").asText();
        String message = root.path("message").asText();
        JsonNode productData = root.path("data").path("productData");

        log.info("code={}, message={}", code, message);

        if (!productData.isArray()) {
            log.warn("⚠️ productData 필드가 없거나 배열이 아님. 실제 값: {}", productData.toString());
            return List.of();
        }

        for (JsonNode item : productData) {
            responseDto.add(CoupangProductResponseDto.of(item));
        }

        log.info("productData 파싱 완료: {}개", responseDto.size());
        return responseDto;
    }

}

