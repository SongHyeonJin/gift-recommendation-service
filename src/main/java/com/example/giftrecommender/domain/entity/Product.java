package com.example.giftrecommender.domain.entity;

import com.example.giftrecommender.domain.entity.keyword.KeywordGroup;
import com.example.giftrecommender.dto.response.ProductResponseDto;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
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
    private String title;

    @Column(columnDefinition = "LONGTEXT")
    private String link;

    @Column(columnDefinition = "LONGTEXT")
    private String imageUrl;

    @Column(nullable = false)
    private Integer price;

    @Column(nullable = false, length = 100)
    private String mallName;

    @Column(length = 100)
    private String brand;

    @Column(length = 100)
    private String category3;

    // 저장 시점 (TTL 관리 목적)
    private Instant cachedAt;

    @ManyToMany
    @JoinTable(
            name = "product_keyword",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "keyword_group_id")
    )
    private List<KeywordGroup> keywordGroups;

    @Builder
    public Product(UUID publicId, String title, String link, String imageUrl,
                   Integer price, String mallName, List<KeywordGroup> keywordGroups,
                   String brand, String category3) {
        this.publicId = publicId;
        this.title = title;
        this.link = link;
        this.imageUrl = imageUrl;
        this.price = price;
        this.mallName = mallName;
        this.keywordGroups = keywordGroups;
        this.brand = brand;
        this.category3 = category3;
    }

    public static Product from(ProductResponseDto dto, List<KeywordGroup> keywordGroups) {
        return Product.builder()
                .title(dto.title().trim().replaceAll("</?b>", " "))
                .link(dto.link())
                .imageUrl(dto.image())
                .price(dto.lprice())
                .mallName(dto.mallName())
                .keywordGroups(keywordGroups)
                .brand(dto.brand())
                .category3(dto.category3())
                .build();
    }

    @PrePersist
    public void prePersist() {
        this.cachedAt = this.cachedAt == null ? Instant.now() : this.cachedAt;
        this.publicId = this.publicId == null ? UUID.randomUUID() : this.publicId;
    }
}
