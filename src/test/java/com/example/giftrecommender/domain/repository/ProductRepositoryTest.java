package com.example.giftrecommender.domain.repository;

import com.example.giftrecommender.domain.entity.Product;
import com.example.giftrecommender.domain.entity.keyword.KeywordGroup;
import com.example.giftrecommender.domain.repository.keyword.KeywordGroupRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductRepositoryTest {

    @Autowired private ProductRepository productRepository;

    @Autowired private KeywordGroupRepository keywordGroupRepository;

    @AfterEach
    void tearDown() {
        productRepository.deleteAllInBatch();
        keywordGroupRepository.deleteAllInBatch();
    }

    @DisplayName("가격 범위와 키워드로 상품을 조회할 수 있다.")
    @Test
    void findTopByTagsAndPriceRange() {
        // given
        KeywordGroup kg1 = keywordGroupRepository.save(new KeywordGroup("운동"));
        KeywordGroup kg2 = keywordGroupRepository.save(new KeywordGroup("러닝화"));
        KeywordGroup kg3 = keywordGroupRepository.save(new KeywordGroup("러닝가방"));
        Product product = createProduct("제목", "링크", "image1", 50000, "mall", List.of(kg1, kg2, kg3));
        productRepository.save(product);

        // when
        List<Product> result = productRepository.findTopByTagsAndPriceRange(List.of("운동", "러닝화", "러닝가방"), 30000, 60000);

        // then
        assertThat(result).hasSize(1).extracting(Product::getTitle).contains("제목");
    }

    @DisplayName("중복 링크 조회 테스트")
    @Test
    void findLinksIn() {
        // given
        Product p1 = createProduct("제목1", "링크1", "image1", 50000, "mall", List.of());
        Product p2 = createProduct("제목", "링크2", "image2", 30000, "mall", List.of());
        productRepository.saveAll(List.of(p1, p2));

        // when
        Set<String> result = productRepository.findLinksIn(Set.of("링크1", "링크3"));

        // then
        assertThat(result).containsOnly("링크1");
    }

    private Product createProduct(String title, String link, String imageUrl, Integer price,
                                  String mallName, List<KeywordGroup> keywordGroups) {
        return Product.builder()
                .publicId(UUID.randomUUID())
                .title(title)
                .link(link)
                .imageUrl(imageUrl)
                .price(price)
                .mallName(mallName)
                .keywordGroups(keywordGroups)
                .build();
    }

}