package com.example.giftrecommender.service;

import com.example.giftrecommender.common.exception.ErrorException;
import com.example.giftrecommender.common.exception.ExceptionEnum;
import com.example.giftrecommender.common.quota.RedisQuotaManager;
import com.example.giftrecommender.domain.entity.*;
import com.example.giftrecommender.domain.entity.keyword.KeywordGroup;
import com.example.giftrecommender.domain.enums.Gender;
import com.example.giftrecommender.domain.enums.SessionStatus;
import com.example.giftrecommender.domain.repository.*;
import com.example.giftrecommender.domain.repository.keyword.KeywordGroupRepository;
import com.example.giftrecommender.dto.request.RecommendationRequestDto;
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
import static org.mockito.BDDMockito.willDoNothing;

@ActiveProfiles("test")
@SpringBootTest
class RecommendationServiceTest {

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private GuestRepository guestRepository;

    @Autowired
    private RecommendationSessionRepository recommendationSessionRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private RecommendationResultRepository recommendationResultRepository;

    @Autowired
    private RecommendationProductRepository recommendationProductRepository;

    @Autowired
    private KeywordGroupRepository keywordGroupRepository;

    @MockBean
    private RedisQuotaManager redisQuotaManager;

    @MockBean
    private ProductImportService productImportService;

    private Guest guest;

    private RecommendationSession recommendationSession;

    private List<Product> products = new ArrayList<>();

    @BeforeEach
    void setUp() {
        guest = createGuest();
        guestRepository.save(guest);

        recommendationSession = createRecommendationSession(guest);
        recommendationSessionRepository.save(recommendationSession);

        // 키워드 저장
        KeywordGroup shoose = keywordGroupRepository.save(new KeywordGroup("러닝화"));
        KeywordGroup bag = keywordGroupRepository.save(new KeywordGroup("러닝가방"));
        KeywordGroup watch = keywordGroupRepository.save(new KeywordGroup("스마트워치"));
        KeywordGroup band = keywordGroupRepository.save(new KeywordGroup("러닝밴드"));

        // 러닝화
        products.add(createProduct("에어플로우 러닝화", "https://ex.com/1", "https://img.com/1.jpg", 94000, "핏스토어", "에어핏", List.of(shoose)));
        products.add(createProduct("바람길 쿠셔닝 슈즈", "https://ex.com/2", "https://img.com/2.jpg", 95000, "컴포트샵", "윈드스텝", List.of(shoose)));
        products.add(createProduct("라이트런 니트슈즈", "https://ex.com/3", "https://img.com/3.jpg", 96000, "러너하우스", "라이트핏", List.of(shoose)));

        // 러닝가방
        products.add(createProduct("트레일러 러닝백", "https://ex.com/4", "https://img.com/4.jpg", 96000, "트레일기어", "트레일러", List.of(bag)));
        products.add(createProduct("에어로 슬링백", "https://ex.com/5", "https://img.com/5.jpg", 98000, "백플래닛", "에어로팩", List.of(bag)));
        products.add(createProduct("하이퍼 경량 백팩", "https://ex.com/6", "https://img.com/6.jpg", 97000, "경량스포츠", "하이퍼백", List.of(bag)));

        // 스마트워치
        products.add(createProduct("핏트래커 스포츠워치", "https://ex.com/7", "https://img.com/7.jpg", 95000, "스마트기어", "핏트래커", List.of(watch)));
        products.add(createProduct("비전핏 스마트밴드", "https://ex.com/8", "https://img.com/8.jpg", 94000, "비전웨어", "비전핏", List.of(watch)));
        products.add(createProduct("펄스핏 워치2", "https://ex.com/9", "https://img.com/9.jpg", 96000, "펄스샵", "펄스핏", List.of(watch)));

        // 러닝밴드
        products.add(createProduct("컴프밴드 프로", "https://ex.com/10", "https://img.com/10.jpg", 97000, "컴프존", "컴프밴드", List.of(band)));
        products.add(createProduct("에어핏 러너밴드", "https://ex.com/11", "https://img.com/11.jpg", 99000, "에어핏코리아", "에어핏", List.of(band)));
        products.add(createProduct("에너지핏 레그밴드", "https://ex.com/12", "https://img.com/12.jpg", 96000, "에너지샵", "에너지핏", List.of(band)));


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

    @DisplayName("조건에 맞는 상품이 있을 경우 키워드당 최대 2개씩 추천 결과 8개가 반환된다.")
    @Test
    void recommendationResultProductsExist() {
        // given
        RecommendationRequestDto request = createRequest();
        willDoNothing().given(redisQuotaManager).acquire();

        // when
        RecommendationResponseDto response =
                recommendationService.recommend(guest.getId(), recommendationSession.getId(), request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.products()).hasSize(8);
    }

    @DisplayName("추천 결과 조회가 정상적으로 동작한다")
    @Test
    void getRecommendationResult() {
        // given
        RecommendationResult result = recommendationResultRepository.save(RecommendationResult.builder()
                .guest(guest)
                .recommendationSession(recommendationSession)
                .keywords(List.of("러닝화", "러닝가방", "스마트워치", "러닝밴드"))
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
        assertThat(response.products()).hasSize(1);
        assertThat(response.products().get(0).title()).contains("러닝화");
    }

    @Test
    @DisplayName("존재하지 않는 게스트일 경우 예외가 발생한다.")
    void guestNotFound() {
        // given
        RecommendationRequestDto request = createRequest();

        // when  then
        assertThatThrownBy(
                () -> recommendationService.recommend(UUID.randomUUID(), recommendationSession.getId(), request))
                .isInstanceOf(ErrorException.class)
                .hasMessageContaining(ExceptionEnum.GUEST_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("존재하지 않는 세션일 경우 예외가 발생한다.")
    void sessionNotFound() {
        // given
        RecommendationRequestDto request = createRequest();

        // when  then
        assertThatThrownBy(
                () -> recommendationService.recommend(guest.getId(), UUID.randomUUID(), request))
                .isInstanceOf(ErrorException.class)
                .hasMessageContaining(ExceptionEnum.SESSION_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("세션 주인이 아닌 경우 예외가 발생한다.")
    void sessionOwnerInvalid() {
        // given
        Guest anotherGuest = guestRepository.save(createGuest());
        RecommendationRequestDto request = createRequest();

        // when  then
        assertThatThrownBy(
                () -> recommendationService.recommend(anotherGuest.getId(), recommendationSession.getId(), request))
                .isInstanceOf(ErrorException.class)
                .hasMessageContaining(ExceptionEnum.SESSION_FORBIDDEN.getMessage());
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

    private RecommendationRequestDto createRequest() {
        return new RecommendationRequestDto("남자친구", "20대", Gender.MALE,50000, 100000,
                "생일", "운동", List.of("러닝화", "러닝가방", "스마트워치", "러닝밴드"));
    }

    private static Product createProduct(String title, String link, String imageUrl,
                                         Integer price, String mall, String brand,
                                         List<KeywordGroup> keywordGroups) {
        return Product.builder()
                .publicId(UUID.randomUUID())
                .title(title)
                .link(link)
                .imageUrl(imageUrl)
                .price(price)
                .mallName(mall)
                .brand(brand)
                .keywordGroups(keywordGroups)
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