package com.example.giftrecommender.domain.entity;

import com.example.giftrecommender.domain.entity.keyword.KeywordGroup;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long id;

    @Column(nullable = false, unique = true)
    private UUID publicId;

    @Column(nullable = false)
    private Long coupangProductId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private Integer price;

    @Lob
    @Column(nullable = false, columnDefinition = "longtext")
    private String imageUrl;

    @Lob
    @Column(nullable = false, columnDefinition = "longtext")
    private String productUrl;

    // 쿠팡 검색 랭킹 정보
    @Column(name = "product_rank")
    private Integer rank;

    // 로켓배송 여부
    private boolean isRocket;

    // 무료배송 여부
    private boolean isFreeShipping;

    @ManyToMany
    @JoinTable(
            name = "product_keyword",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "keyword_group_id")
    )
    private List<KeywordGroup> keywordGroups;

    // 저장 시점 (TTL 관리 목적)
    @Column(nullable = false)
    private LocalDateTime cachedAt;

    @Builder
    public Product(UUID publicId, Long coupangProductId, String title, Integer price, String imageUrl,
                   String productUrl, Integer rank, boolean isRocket, boolean isFreeShipping,
                   List<KeywordGroup> keywordGroups) {
        this.publicId = publicId;
        this.coupangProductId = coupangProductId;
        this.title = title;
        this.price = price;
        this.imageUrl = imageUrl;
        this.productUrl = productUrl;
        this.rank = rank;
        this.isRocket = isRocket;
        this.isFreeShipping = isFreeShipping;
        this.keywordGroups = keywordGroups;
    }

    @PrePersist
    public void prePersist() {
        this.cachedAt = this.cachedAt == null ? LocalDateTime.now() : this.cachedAt;
        this.publicId = this.publicId == null ? UUID.randomUUID() : this.publicId;
    }
}
