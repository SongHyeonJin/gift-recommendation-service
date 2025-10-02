package com.example.giftrecommender.domain.entity;

import com.example.giftrecommender.domain.enums.Age;
import com.example.giftrecommender.domain.enums.Gender;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "crawling_product")
@Getter
@NoArgsConstructor
public class CrawlingProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "crawling_product_id")
    private Long id;

    // 원본 상품명
    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    // 사용자에게 표시할 상품명
    @Column(name = "display_name", length = 255)
    private String displayName;

    // 가격 (원 단위)
    @Column(nullable = false)
    private Integer price;

    // 대표 이미지 URL
    @Column(columnDefinition = "TEXT", nullable = false)
    private String imageUrl;

    // 상품 상세 페이지 링크
    @Column(name = "product_url", nullable = false, length = 512, unique = true)
    private String productUrl;

    // 카테고리명
    @Column(length = 100)
    private String category;

    // 검색/추천용 키워드 배열
    @ElementCollection
    @CollectionTable(name = "crawling_product_keywords", joinColumns = @JoinColumn(name = "crawling_product_id"))
    @Column(name = "keyword")
    private List<String> keywords;

    // 리뷰 개수
    @Column(name = "review_count")
    private Integer reviewCount;

    // 별점 (0~5)
    @Column(precision = 2, scale = 1)
    private BigDecimal rating;

    // 판매자명
    @Column(name = "seller_name", length = 100)
    private String sellerName;

    // 플랫폼명 (예: 텐바이텐, 네이버)
    @Column(length = 50)
    private String platform;

    // 점수 (자동 + 수동)
    @Column
    private Integer score = 0;

    // 관리자 수동 점수 부여 여부
    @Column(name = "admin_check")
    private Boolean adminCheck = false;

    // 관리자 컨펌 여부
    @Column(name = "is_confirmed")
    private Boolean isConfirmed = false;

    @Column(name = "vector_point_id", length = 100)
    private String vectorPointId;

    @Column(name = "embedding_model", length = 50)
    private String embeddingModel;

    @Column(name = "embedding_ready")
    private Boolean embeddingReady = false;

    // 성별 태그
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender = Gender.ANY;

    // 연령대 태그
    @Enumerated(EnumType.STRING)
    @Column(length = 15)
    private Age age = Age.ANY;

    // 생성/수정 시각
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public void addScore(int score) {
        this.score += score;
    }

    public void changeAdminCheck(boolean adminCheck) {
        this.adminCheck = adminCheck;
    }

    public void changeConfirmed(boolean confirmed) {
        this.isConfirmed = confirmed;
    }

    public void changeAge(Age age) {
        this.age = age;
    }

    public void changeGender(Gender gender) {
        this.gender = gender;
    }
    public void changeOriginalName(String originalName) { this.originalName = originalName; }
    public void changeDisplayName(String displayName) { this.displayName = displayName; }
    public void changePrice(Integer price) { this.price = price; }
    public void changeImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void changeProductUrl(String productUrl) { this.productUrl = productUrl; }
    public void changeCategory(String category) { this.category = category; }
    public void changeKeywords(List<String> keywords) { this.keywords = keywords; }
    public void changeSellerName(String sellerName) { this.sellerName = sellerName; }
    public void changePlatform(String platform) { this.platform = platform; }
    public void markEmbedding(String pointId, String model, boolean ready) {
        this.vectorPointId = pointId;
        this.embeddingModel = model;
        this.embeddingReady = ready;
    }
    public void markEmbeddingReady() {
        this.embeddingReady = true;
    }

    @Builder
    public CrawlingProduct(String originalName, String displayName, Integer price, String imageUrl,
                           String productUrl, String category, List<String> keywords, Integer reviewCount,
                           BigDecimal rating,  Integer score, String sellerName, String platform, Gender gender, Age age) {
        this.originalName = originalName;
        this.displayName = displayName;
        this.price = price;
        this.imageUrl = imageUrl;
        this.productUrl = productUrl;
        this.category = category;
        this.keywords = keywords;
        this.reviewCount = reviewCount;
        this.rating = rating;
        this.score = score != null ? score : 0;
        this.sellerName = sellerName;
        this.platform = platform;
        this.gender = gender;
        this.age = age;
    }
}

