package com.example.giftrecommender.vector;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.openai.client.OpenAIClient;
import com.openai.errors.RateLimitException;
import com.openai.models.embeddings.EmbeddingCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "vector", name = "enabled", havingValue = "true")
public class EmbeddingService {

    private static final String MODEL = "text-embedding-3-small"; // 1536 dim
    private static final int EXPECTED_DIM = 1536;

    private final OpenAIClient client;
    private final boolean enabled;

    private final Cache<String, List<Float>> cache = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(Duration.ofHours(12))
            .build();

    public EmbeddingService(
            OpenAIClient client,
            @Value("${openai.enabled:true}") boolean enabled
    ) {
        this.client = client;
        this.enabled = enabled;
    }

    public List<Float> embed(String text) throws Exception {
        // 캐시 확인
        List<Float> cached = cache.getIfPresent(text);
        if (cached != null) {
            log.debug("[EMBED][CACHE] hit textLen={}, firstChars='{}...'", text.length(), safeHead(text));
            return cached;
        }

        long t0 = System.nanoTime();
        List<Float> result;

        if (!enabled) {
            result = dummyVector(EXPECTED_DIM, text);
            cache.put(text, result);
            log.info("[EMBED][DUMMY] enabled=false, textLen={}, took={}ms", text.length(), msSince(t0));
            return result;
        }

        int maxRetries = 3;
        int attempt = 0;
        Duration backoff = Duration.ofMillis(500);

        while (true) {
            attempt++;
            try {
                log.debug("[EMBED][CALL] model={}, textLen={}, attempt={}", MODEL, text.length(), attempt);

                EmbeddingCreateParams params = EmbeddingCreateParams.builder()
                        .model(MODEL)
                        .input(text)
                        .encodingFormat(EmbeddingCreateParams.EncodingFormat.FLOAT)
                        .build();

                result = client.embeddings()
                        .create(params)
                        .data()
                        .get(0)
                        .embedding();

                if (result.size() != EXPECTED_DIM) {
                    throw new IllegalStateException("Embedding size mismatch: expected=" +
                            EXPECTED_DIM + ", actual=" + result.size());
                }

                cache.put(text, new ArrayList<>(result));
                log.debug("[EMBED][OK] dim={}, took={}ms, attempt={}", result.size(), msSince(t0), attempt);
                return result;

            } catch (RateLimitException e) {
                // 429 전용 처리
                log.warn("[EMBED][RETRY] RateLimit attempt={}/{}, backoff={}ms, msg={}",
                        attempt, maxRetries, backoff.toMillis(), e.getMessage());
                if (attempt >= maxRetries) {
                    log.error("[EMBED][FAIL] permanently failed (rate limit) after {} attempts, head={}",
                            attempt, safeHead(text));
                    throw e;
                }
                sleep(backoff);
                backoff = backoff.multipliedBy(2);

            } catch (Exception e) {
                // 기타 모든 예외: 타임아웃/네트워크 포함
                boolean timeoutLike = isTimeoutLike(e);
                log.warn("[EMBED][RETRY] {} attempt={}/{}, backoff={}ms, msg={}",
                        timeoutLike ? "TimeoutLike" : e.getClass().getSimpleName(),
                        attempt, maxRetries, backoff.toMillis(), e.getMessage());

                if (attempt >= maxRetries) {
                    log.error("[EMBED][FAIL] permanently failed after {} attempts, head={}",
                            attempt, safeHead(text));
                    throw e;
                }
                sleep(backoff);
                backoff = backoff.multipliedBy(2);
            }
        }
    }

    /** e 혹은 cause 체인에 타임아웃성 예외가 있는지 판별 */
    private static boolean isTimeoutLike(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException || t instanceof HttpTimeoutException
                    || t instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private static String safeHead(String s) {
        return s.length() <= 10 ? s : s.substring(0, 10);
    }

    private static long msSince(long t0) {
        return (System.nanoTime() - t0) / 1_000_000;
    }

    private static List<Float> dummyVector(int dim, String seed) {
        List<Float> v = new ArrayList<>(dim);
        int h = seed.hashCode();
        for (int i = 0; i < dim; i++) {
            h = 31 * h + i;
            float val = (h % 1000) / 1000.0f;
            v.add(val);
        }
        return v;
    }
}
