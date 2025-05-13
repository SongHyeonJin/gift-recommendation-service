package com.example.giftrecommender.domain.entity.keyword;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KeywordGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "keyword_group_id")
    private Long id;

    @Column(nullable = false, unique = true)
    private String mainKeyword;

    public KeywordGroup(String mainKeyword) {
        this.mainKeyword = mainKeyword;
    }

}
