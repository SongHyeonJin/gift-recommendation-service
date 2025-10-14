package com.example.giftrecommender.vector;

import com.example.giftrecommender.domain.repository.CrawlingProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "vector", name = "enabled", havingValue = "true")
public class QdrantKeywordBackfillService {

    private final CrawlingProductRepository productRepository;
    private final QdrantAdminClient qdrantAdminClient;

    public void backfillKeywordsToQdrant() {
        List<BackfillIdView> rows = productRepository.findAllIdsForQdrantKeywordBackfill();
        log.info("[BACKFILL] candidates={}", rows.size());

        int ok = 0;
        int fail = 0;

        for (BackfillIdView v : rows) {
            Long pointId = toQdrantPointId(v.getVectorPointId(), v.getId());

            // id로 키워드만 별도 조회
            List<String> keywords = productRepository.findKeywordsById(v.getId());
            List<String> normalized = normalizeKeywords(keywords);

            if (pointId == null || normalized.isEmpty()) {
                log.warn("[BACKFILL][SKIP] id={}, pointId={}, keywords.size={}",
                        v.getId(), v.getVectorPointId(), normalized.size());
                continue;
            }

            try {
                qdrantAdminClient.setKeywordsForPoint(pointId, normalized);
                ok++;
            } catch (Exception e) {
                fail++;
                log.error("[BACKFILL][FAIL] id={}, pointId={}, err={}",
                        v.getId(), pointId, e.toString());
            }
        }

        log.info("[BACKFILL][DONE] success={}, fail={}", ok, fail);
    }

    /**
     * Qdrant point id로 보낼 적절한 타입 결정:
     * - vectorPointId가 존재하면 Long 그대로 사용
     * - 없으면 DB id(Long) 사용 (초기 업서트가 DB id를 포인트로 쓴 경우)
     */
    private Long toQdrantPointId(Long vectorPointId, Long dbId) {
        if (vectorPointId != null) {
            return vectorPointId;
        }
        return (dbId != null) ? dbId : null;
    }

    private List<String> normalizeKeywords(List<String> keywords) {
        if (keywords == null) return List.of();
        List<String> result = new ArrayList<>();
        for (String s : keywords) {
            if (s == null) continue;
            String t = s.trim();
            if (!t.isEmpty() && !result.contains(t)) {
                result.add(t);
            }
        }
        return result;
    }
}
