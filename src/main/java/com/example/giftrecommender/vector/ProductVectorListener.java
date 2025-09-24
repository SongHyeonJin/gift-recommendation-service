package com.example.giftrecommender.vector;

import com.example.giftrecommender.domain.repository.CrawlingProductRepository;
import com.example.giftrecommender.vector.event.ProductCreatedEvent;
import com.openai.errors.RateLimitException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "vector", name = "enabled", havingValue = "true")
public class ProductVectorListener {

    private final ProductVectorService productVectorService;
    private final CrawlingProductRepository crawlingProductRepository;
    private final ProductVectorStatusService statusService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductCreated(ProductCreatedEvent event) {
        Boolean alreadyReady = crawlingProductRepository.isEmbeddingReady(event.productId());
        if (Boolean.TRUE.equals(alreadyReady)) {
            log.info("[VECTOR] skip (already ready) id={}", event.productId());
            return;
        }

        int maxRetries = 3;
        long backoffMs = 500L;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // OpenAI → Qdrant 업서트
                productVectorService.upsertProduct(
                        event.productId(),
                        event.displayName(),
                        event.price()
                );

                // DB 상태 갱신 (새 트랜잭션으로 처리)
                statusService.markEmbeddingReady(event.productId());

                log.info("[VECTOR] upsert ok id={}, attempt={}", event.productId(), attempt);
                return;

            } catch (RateLimitException e) {
                log.warn("[VECTOR] OpenAI 429, attempt={}/{}; retry in {}ms. msg={}",
                        attempt, maxRetries, backoffMs, e.getMessage());
            } catch (Exception e) {
                log.error("[VECTOR] upsert failed id={}, attempt={}, cause={}",
                        event.productId(), attempt, e.toString());
            }

            try {
                Thread.sleep(backoffMs);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            backoffMs *= 2;
        }

        log.error("[VECTOR] upsert permanently failed id={}", event.productId());
    }

}

