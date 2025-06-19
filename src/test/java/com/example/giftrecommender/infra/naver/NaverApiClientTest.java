package com.example.giftrecommender.infra.naver;

import com.example.giftrecommender.common.exception.ErrorException;
import com.example.giftrecommender.common.exception.ExceptionEnum;
import com.example.giftrecommender.common.quota.RedisQuotaManager;
import com.example.giftrecommender.dto.response.ProductResponseDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
class NaverApiClientTest {
    static class TestReflection {
        public static void setField(Object target, String fieldName, Object value) {
            try {
                Field f = target.getClass().getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(target, value);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Mock
    private RedisQuotaManager quotaManager;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private NaverApiClient naverApiClient;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        TestReflection.setField(naverApiClient, "clientId", "test-client-id");
        TestReflection.setField(naverApiClient, "clientSecret", "test-client-secret");
    }

    @DisplayName("네이버 API에서 정상적으로 상품을 파싱한다.")
    @Test
    void testSearch_success() throws Exception {
        // given
        doNothing().when(quotaManager).acquire();

        String json = """
        {
          "items": [
            {
              "title": "테스트 상품",
              "link": "http://example.com",
              "image": "http://example.com/image.jpg",
              "lprice": "12345",
              "mallName": "테스트몰",
              "brand": "브랜드",
              "category3": "카테고리"
            }
          ]
        }
        """;

        JsonNode mockResponse = new ObjectMapper().readTree(json);

        ResponseEntity<JsonNode> responseEntity = new ResponseEntity<>(mockResponse, HttpStatus.OK);
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(responseEntity);

        // when
        List<ProductResponseDto> result = naverApiClient.search("테스트", 1, 10);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("테스트 상품");
        assertThat(result.get(0).lprice()).isEqualTo(12345);
    }

}