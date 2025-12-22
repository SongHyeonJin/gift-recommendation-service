package com.example.giftrecommender.vector.event;

/**
 * 상품이 DB에 저장된 후 발행되는 이벤트.
 * - CrawlingProductSaver.save()에서 발행
 * - ProductVectorListener 등에서 구독하여 벡터화/업서트 실행
 */
public record ProductCreatedEvent(
        Long productId,
        String displayName,
        long price,
        String category,
        String shortDescription
) { }
