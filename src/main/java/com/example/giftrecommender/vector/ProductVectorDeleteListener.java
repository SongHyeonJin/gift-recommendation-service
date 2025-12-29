package com.example.giftrecommender.vector;

import com.example.giftrecommender.vector.event.ProductDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "vector", name = "enabled", havingValue = "true")
public class ProductVectorDeleteListener {

    private final ProductVectorService productVectorService;

    /**
     * DB 트랜잭션이 "성공적으로 커밋된 후"에만 실행된다.
     * - DB 삭제 실패/롤백이면 호출되지 않음
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductDeleted(ProductDeletedEvent event) {
        try {
            productVectorService.deleteProduct(event.productId());
        } catch (Exception e) {
            log.error("[VECTOR][AFTER_COMMIT] delete failed - productId={}", event.productId(), e);
        }
    }
}