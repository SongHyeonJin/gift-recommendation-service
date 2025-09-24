package com.example.giftrecommender.vector;

import com.example.giftrecommender.domain.repository.CrawlingProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductVectorStatusService {

    private final CrawlingProductRepository crawlingProductRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markEmbeddingReady(Long productId) {
        int updated = crawlingProductRepository.markEmbeddingReady(productId);
        if (updated != 1) {
            throw new IllegalStateException("Vector flag update failed. id=" + productId);
        }
    }
}
