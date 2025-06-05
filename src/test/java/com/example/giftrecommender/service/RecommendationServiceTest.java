package com.example.giftrecommender.service;

import com.example.giftrecommender.common.exception.ErrorException;
import com.example.giftrecommender.common.exception.ExceptionEnum;
import com.example.giftrecommender.common.quota.RedisQuotaManager;
import com.example.giftrecommender.domain.entity.*;
import com.example.giftrecommender.domain.entity.keyword.KeywordGroup;
import com.example.giftrecommender.domain.enums.SessionStatus;
import com.example.giftrecommender.domain.repository.*;
import com.example.giftrecommender.domain.repository.keyword.KeywordGroupRepository;
import com.example.giftrecommender.dto.response.RecommendationResponseDto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@SpringBootTest
class RecommendationServiceTest {

    @Autowired private RecommendationService recommendationService;

    @Autowired private GuestRepository guestRepository;

    @Autowired private RecommendationSessionRepository recommendationSessionRepository;

    @Autowired private ProductRepository productRepository;

    @Autowired private RecommendationResultRepository recommendationResultRepository;

    @Autowired private RecommendationProductRepository recommendationProductRepository;

    @Autowired private KeywordGroupRepository keywordGroupRepository;

    @MockBean private RedisQuotaManager redisQuotaManager;

    @MockBean private ProductImportService productImportService;

    private Guest guest;

    private RecommendationSession recommendationSession;

    private List<Product> products = new ArrayList<>();

    @BeforeEach
    void setUp() {
        guest = createGuest();
        guestRepository.save(guest);

        recommendationSession = createRecommendationSession("테스트", guest);
        recommendationSessionRepository.save(recommendationSession);

        KeywordGroup k1 = new KeywordGroup("여자친구");
        KeywordGroup k2 = new KeywordGroup("생일");
        KeywordGroup k3 = new KeywordGroup("악세서리");
        KeywordGroup k4 = new KeywordGroup("반지");
        KeywordGroup k5 = new KeywordGroup("금");
        keywordGroupRepository.saveAll(List.of(k1, k2, k3, k4, k5));

        for (int i = 1; i <= 4; i++) {
            String title = switch (i) {
                case 1 -> "여자친구 생일 선물 금 반지";
                case 2 -> "생일 반지 추천 여자친구용";
                case 3 -> "고급 금 반지 악세서리 선물";
                case 4 -> "여친 기념일 금 반지 악세서리";
                default -> "기본 반지";
            };
            products.add(createProduct(title, "https://example.com/" + i,
                    "https://img.com/" + i + ".jpg", 90000, "브랜드" + i, List.of(k1, k2, k3, k4, k5)));
        }
        productRepository.saveAll(products);
    }

    @AfterEach
    void tearDown() {
        recommendationProductRepository.deleteAllInBatch();
        recommendationResultRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        keywordGroupRepository.deleteAllInBatch();
        recommendationSessionRepository.deleteAllInBatch();
        guestRepository.deleteAllInBatch();
    }

    @DisplayName("조건에 맞는 상품이 있을 경우 추천 결과  4개가 성공적으로 반환된다.")
    @Test
    void recommendationResultProductsExist() {
        // given
        List<String> keywords = List.of("여자친구", "5~10만원", "생일", "악세서리", "반지", "금");
        when(redisQuotaManager.canCall()).thenReturn(true);

        // when
        RecommendationResponseDto response =
                recommendationService.recommend(guest.getId(), recommendationSession.getId(), keywords);

        // then
        assertThat(response).isNotNull();
        assertThat(response.products()).hasSize(4);
        assertThat(response.products()).allSatisfy(product ->
                assertThat(product.title()).contains("반지")
        );
    }

    @DisplayName("추천 결과 조회가 정상적으로 동작한다")
    @Test
    void getRecommendationResult() {
        // given
        RecommendationResult result = recommendationResultRepository.save(RecommendationResult.builder()
                .guest(guest)
                .recommendationSession(recommendationSession)
                .keywords(List.of("악세서리", "반지", "금", "여자친구", "생일"))
                .build());

        recommendationProductRepository.save(
                RecommendationProduct.builder()
                        .recommendationResult(result)
                        .product(products.get(0))
                        .build()
        );

        // when
        RecommendationResponseDto response =
                recommendationService.getRecommendationResult(guest.getId(), recommendationSession.getId());

        // then
        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("테스트");
        assertThat(response.products()).hasSize(1);
        assertThat(response.products().get(0).title()).contains("반지");
    }

    @Test
    @DisplayName("존재하지 않는 게스트일 경우 예외가 발생한다.")
    void guestNotFound() {
        // given
        List<String> keywords = List.of("반지", "5~10만원");

        // when  then
        assertThatThrownBy(
                () -> recommendationService.recommend(UUID.randomUUID(), recommendationSession.getId(), keywords))
                .isInstanceOf(ErrorException.class)
                .hasMessageContaining(ExceptionEnum.GUEST_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("존재하지 않는 세션일 경우 예외가 발생한다.")
    void sessionNotFound() {
        // given
        List<String> keywords = List.of("반지", "5~10만원");

        // when  then
        assertThatThrownBy(
                () -> recommendationService.recommend(guest.getId(), UUID.randomUUID(), keywords))
                .isInstanceOf(ErrorException.class)
                .hasMessageContaining(ExceptionEnum.SESSION_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("세션 주인이 아닌 경우 예외가 발생한다.")
    void sessionOwnerInvalid() {
        // given
        Guest anotherGuest = guestRepository.save(createGuest());
        List<String> keywords = List.of("반지", "5~10만원");

        // when  then
        assertThatThrownBy(
                () -> recommendationService.recommend(anotherGuest.getId(), recommendationSession.getId(), keywords))
                .isInstanceOf(ErrorException.class)
                .hasMessageContaining(ExceptionEnum.SESSION_FORBIDDEN.getMessage());
    }

    @Test
    @DisplayName("쿼터 초과 시 예외가 발생한다.")
    void quotaExceeded() {
        // given
        productRepository.deleteAll();
        when(redisQuotaManager.canCall()).thenReturn(false);
        List<String> keywords = List.of("여자친구", "5~10만원", "생일", "악세서리", "반지", "금");

        // when  then
        assertThatThrownBy(
                () -> recommendationService.recommend(guest.getId(), recommendationSession.getId(), keywords))
                .isInstanceOf(ErrorException.class)
                .hasMessageContaining(ExceptionEnum.QUOTA_EXCEEDED.getMessage());
    }

    @Test
    @DisplayName("추천 결과 조회 시 추천 이력이 없으면 예외가 발생한다.")
    void recommendationResultNotFound() {
        // when  then
        assertThatThrownBy(
                () -> recommendationService.getRecommendationResult(guest.getId(), recommendationSession.getId()))
                .isInstanceOf(ErrorException.class)
                .hasMessageContaining(ExceptionEnum.RESULT_NOT_FOUND.getMessage());
    }

    private static Product createProduct(String title, String link, String imageUrl,
                                         Integer price, String mall, List<KeywordGroup> keywordGroups) {
        return Product.builder()
                .publicId(UUID.randomUUID())
                .title(title)
                .link(link)
                .imageUrl(imageUrl)
                .price(price)
                .mallName(mall)
                .keywordGroups(keywordGroups)
                .build();
    }


    private static RecommendationSession createRecommendationSession(String name, Guest guest) {
        return RecommendationSession.builder()
                .id(UUID.randomUUID())
                .name(name)
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