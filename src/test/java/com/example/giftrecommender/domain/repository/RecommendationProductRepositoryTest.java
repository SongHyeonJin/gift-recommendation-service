package com.example.giftrecommender.domain.repository;

import com.example.giftrecommender.domain.entity.*;
import com.example.giftrecommender.domain.enums.SessionStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RecommendationProductRepositoryTest {

    @Autowired private GuestRepository guestRepository;

    @Autowired private RecommendationSessionRepository recommendationSessionRepository;

    @Autowired private ProductRepository productRepository;

    @Autowired private RecommendationProductRepository recommendationProductRepository;

    @Autowired private RecommendationResultRepository recommendationResultRepository;

    private Guest guest;

    private RecommendationSession recommendationSession;

    @BeforeEach
    void setUp() {
        guest = createGuest();
        guestRepository.save(guest);

        recommendationSession = createRecommendationSession(guest);
        recommendationSessionRepository.save(recommendationSession);
    }

    @AfterEach
    void tearDown() {
        recommendationProductRepository.deleteAllInBatch();
        recommendationResultRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        recommendationSessionRepository.deleteAllInBatch();
        guestRepository.deleteAllInBatch();
    }

    @DisplayName("추천 결과 ID로 상품 목록을 조회할 수 있다.")
    @Test
    void findProductsByResultId() {
        // given

        Product product = createProduct("제목", "링크", "image", 10000, "mall");
        productRepository.save(product);

        RecommendationResult result = createRecommendationResult(guest, recommendationSession);
        recommendationResultRepository.save(result);

        recommendationProductRepository.save(RecommendationProduct.builder()
                .recommendationResult(result)
                .product(product)
                .build());

        // when
        List<Product> resultList = recommendationProductRepository.findProductsByResultId(result.getId());

        // then
        assertThat(resultList).hasSize(1).contains(product);
    }

    private static RecommendationResult createRecommendationResult(Guest guest, RecommendationSession recommendationSession) {
        return RecommendationResult.builder()
                .guest(guest)
                .recommendationSession(recommendationSession)
                .keywords(List.of())
                .build();
    }

    private static Product createProduct(String title, String link, String imageUrl, Integer price, String mallName) {
        return Product.builder()
                .publicId(UUID.randomUUID())
                .title(title)
                .link(link)
                .imageUrl(imageUrl)
                .price(price)
                .mallName(mallName)
                .keywordGroups(List.of())
                .build();
    }

    private static RecommendationSession createRecommendationSession(Guest guest) {
        return RecommendationSession.builder()
                .id(UUID.randomUUID())
                .status(SessionStatus.PENDING)
                .guest(guest)
                .build();
    }

    private static Guest createGuest() {
        return Guest.builder()
                .id(UUID.randomUUID())
                .build();
    }

}