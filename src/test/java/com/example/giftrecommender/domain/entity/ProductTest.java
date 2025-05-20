package com.example.giftrecommender.domain.entity;

import com.example.giftrecommender.domain.entity.keyword.KeywordGroup;
import com.example.giftrecommender.dto.response.ProductResponseDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
class ProductTest {

    @DisplayName("Product.from() 정적 팩토리 메서드는 DTO를 기반으로 올바르게 생성되어야 한다.")
    @Test
    void testFromDto() {
        // given
        ProductResponseDto dto = new ProductResponseDto(UUID.randomUUID(), "title <b>bold</b>", "link", "image", 10000, "mall");
        List<KeywordGroup> keywords = List.of(new KeywordGroup("여자친구"));

        // when
        Product product = Product.from(dto, keywords);

        // then
        assertThat("title  bold ").isEqualTo(product.getTitle());
        assertThat("link").isEqualTo(product.getLink());
        assertThat(10000).isEqualTo(product.getPrice());
        assertThat(product.getKeywordGroups()).extracting(KeywordGroup::getMainKeyword)
                .containsExactly("여자친구");
    }

}