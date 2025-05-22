package com.example.giftrecommender.domain.repository.keyword;

import com.example.giftrecommender.domain.entity.keyword.KeywordGroup;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class KeywordGroupRepositoryTest {

    @Autowired private KeywordGroupRepository keywordGroupRepository;

    @DisplayName("여러 키워드로 키워드 그룹을 조회할 수 있다.")
    @Test
    void findByMainKeywordIn() {
        // given
        keywordGroupRepository.save(new KeywordGroup("운동"));
        keywordGroupRepository.save(new KeywordGroup("건강"));

        // when
        List<KeywordGroup> results = keywordGroupRepository.findByMainKeywordIn(List.of("운동", "건강", "다이어트"));

        // then
        assertThat(results).hasSize(2);
        assertThat(results).extracting(KeywordGroup::getMainKeyword).contains("운동", "건강");
    }

}