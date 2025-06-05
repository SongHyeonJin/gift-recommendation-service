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

        // 키워드 저장
        KeywordGroup girlfriend = keywordGroupRepository.save(new KeywordGroup("여자친구"));
        KeywordGroup birthday = keywordGroupRepository.save(new KeywordGroup("생일"));
        KeywordGroup moodlight = keywordGroupRepository.save(new KeywordGroup("무드등"));
        KeywordGroup ring = keywordGroupRepository.save(new KeywordGroup("반지"));
        KeywordGroup gold = keywordGroupRepository.save(new KeywordGroup("금"));

        // 조합1: ["여자친구","무드등","생일"]
        products.add(createProduct("스텔라 라이트 오브제", "https://ex.com/1", "https://img.com/1.jpg", 95000, "빛의정원", "라이트하우스", List.of(girlfriend, moodlight, birthday)));
        products.add(createProduct("드림캐처 별빛 조명", "https://ex.com/2", "https://img.com/2.jpg", 93000, "힐링하우스", "별조명코리아", List.of(girlfriend, moodlight, birthday)));
        products.add(createProduct("밤하늘 테이블 램프", "https://ex.com/3", "https://img.com/3.jpg", 97000, "조명마을", "무드펄", List.of(girlfriend, moodlight, birthday)));

        // 조합2: ["여자친구","반지","생일"]
        products.add(createProduct("러브메탈 핑크링", "https://ex.com/4", "https://img.com/4.jpg", 94000, "러브링스몰", "러브링스", List.of(girlfriend, ring, birthday)));
        products.add(createProduct("메르시 볼드링", "https://ex.com/5", "https://img.com/5.jpg", 95000, "모던쥬얼", "메르시", List.of(girlfriend, ring, birthday)));
        products.add(createProduct("심장박동 골드링", "https://ex.com/6", "https://img.com/6.jpg", 96000, "하트골드샵", "골드하트", List.of(girlfriend, ring, birthday)));

        // 조합3: ["여자친구","금","생일"]
        products.add(createProduct("클래식 진주 드롭귀걸이", "https://ex.com/7", "https://img.com/7.jpg", 96000, "로즈앤골드", "클래식뷰", List.of(girlfriend, gold, birthday)));
        products.add(createProduct("헬렌 체인 뱅글", "https://ex.com/8", "https://img.com/8.jpg", 98000, "골드하임", "헬렌주얼리", List.of(girlfriend, gold, birthday)));
        products.add(createProduct("루체아 로즈 팬던트", "https://ex.com/9", "https://img.com/9.jpg", 97000, "핑크주얼", "루체아", List.of(girlfriend, gold, birthday)));

        // 조합4: ["여자친구","무드등","반지","생일"]
        products.add(createProduct("피오레 파스텔 세트", "https://ex.com/10", "https://img.com/10.jpg", 95000, "조이쥬얼", "피오레라", List.of(girlfriend, moodlight, ring, birthday)));
        products.add(createProduct("미드나잇 앤써 링박스", "https://ex.com/11", "https://img.com/11.jpg", 94000, "빛앤링", "앤써링", List.of(girlfriend, moodlight, ring, birthday)));
        products.add(createProduct("글로우 뷰티 조명키트", "https://ex.com/12", "https://img.com/12.jpg", 96000, "예쁜반지샵", "글로우존", List.of(girlfriend, moodlight, ring, birthday)));

        // 조합5: ["여자친구","무드등","금","생일"]
        products.add(createProduct("라파엘로 캔들보틀", "https://ex.com/13", "https://img.com/13.jpg", 97000, "골드앤라이트", "라파엘로", List.of(girlfriend, moodlight, gold, birthday)));
        products.add(createProduct("루미에르 스톤 목걸이", "https://ex.com/14", "https://img.com/14.jpg", 99000, "다이아주얼", "루미에르", List.of(girlfriend, moodlight, gold, birthday)));
        products.add(createProduct("플레르 노블 링세트", "https://ex.com/15", "https://img.com/15.jpg", 96000, "럭스골드", "플레르", List.of(girlfriend, moodlight, gold, birthday)));

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

    @DisplayName("조건에 맞는 상품이 있을 경우 추천 결과  10개가 성공적으로 반환된다.")
    @Test
    void recommendationResultProductsExist() {
        // given
        List<String> keywords = List.of("여자친구", "5~10만원", "생일", "무드등", "반지", "금");
        when(redisQuotaManager.canCall()).thenReturn(true);

        // when
        RecommendationResponseDto response =
                recommendationService.recommend(guest.getId(), recommendationSession.getId(), keywords);

        // then
        assertThat(response).isNotNull();
        assertThat(response.products()).hasSize(10);
    }

    @DisplayName("추천 결과 조회가 정상적으로 동작한다")
    @Test
    void getRecommendationResult() {
        // given
        RecommendationResult result = recommendationResultRepository.save(RecommendationResult.builder()
                .guest(guest)
                .recommendationSession(recommendationSession)
                .keywords(List.of("여자친구", "무드등", "반지", "금", "생일"))
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
        assertThat(response.products().get(0).title()).contains("오브제");
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
        List<String> keywords = List.of("여자친구", "5~10만원", "생일", "무드등", "반지", "금");

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