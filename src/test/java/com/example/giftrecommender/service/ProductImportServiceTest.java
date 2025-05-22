package com.example.giftrecommender.service;


import com.example.giftrecommender.domain.entity.Product;
import com.example.giftrecommender.domain.repository.ProductRepository;
import com.example.giftrecommender.domain.repository.keyword.KeywordGroupRepository;
import com.example.giftrecommender.dto.response.ProductResponseDto;
import com.example.giftrecommender.infra.naver.NaverApiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@SpringBootTest
class ProductImportServiceTest {

    @Autowired private ProductImportService productImportService;

    @Autowired private ProductRepository productRepository;

    @Autowired private KeywordGroupRepository keywordGroupRepository;

    @MockBean private NaverApiClient naverApiClient;

    @AfterEach
    void tearDown() {
        productRepository.deleteAllInBatch();
        keywordGroupRepository.deleteAllInBatch();
    }

    @DisplayName("상품이 조건에 맞게 저장된다")
    @Test
    void importUntilEnoughPriceMatches() {
        // given
        List<String> tags = List.of("감성", "우아한");
        String priceKeyword = "5~10만원";
        String receiver = "연인";
        String reason = "생일";

        ProductResponseDto mockDto = new ProductResponseDto(
                UUID.randomUUID(),
                "우아한 감성 커플 선물",
                "https://example.com/product1",
                "https://example.com/image1.jpg",
                89000,
                "쿠팡"
        );

        when(naverApiClient.search(anyString(), eq(1), eq(100)))
                .thenReturn(List.of(mockDto));
        when(naverApiClient.search(anyString(), intThat(i -> i > 1), eq(100)))
                .thenReturn(List.of());

        // when
        productImportService.importUntilEnough(tags, priceKeyword, receiver, reason, 1);

        // then
        List<Product> saved = productRepository.findAll();
        assertThat(saved).hasSize(1);
        Product savedProduct = saved.get(0);
        assertThat(savedProduct.getPrice()).isEqualTo(89000);
        assertThat(savedProduct.getTitle()).contains("감성");
        assertThat(savedProduct.getLink()).isEqualTo("https://example.com/product1");
    }

}