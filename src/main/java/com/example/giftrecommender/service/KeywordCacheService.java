package com.example.giftrecommender.service;

import com.example.giftrecommender.domain.entity.keyword.KeywordGroup;
import com.example.giftrecommender.domain.repository.keyword.KeywordGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeywordCacheService {

    private final KeywordGroupRepository keywordGroupRepository;
    private final Map<String, KeywordGroup> cache = new ConcurrentHashMap<>();
    private final Map<String, Object> lockMap = new ConcurrentHashMap<>();

    @Transactional
    public KeywordGroup getOrCreate(String keyword) {
        // 1. 캐시 확인
        KeywordGroup existing = cache.get(keyword);
        if (existing != null) return existing;

        // 2. 키워드별 락으로 동기화
        Object lock = lockMap.computeIfAbsent(keyword, k -> new Object());

        synchronized (lock) {
            // 3. 락 획득 후 캐시 재확인 (다른 쓰레드가 먼저 처리했을 수도 있음)
            existing = cache.get(keyword);
            if (existing != null) return existing;

            // 4. 중복 삽입 무시하는 upsert
            keywordGroupRepository.upsertIgnore(keyword);

            // 5. 반드시 재조회
            KeywordGroup saved = keywordGroupRepository.findByMainKeywordIn(Set.of(keyword))
                    .stream().findFirst()
                    .orElseThrow(() -> {
                        log.error("키워드 저장 후 조회 실패 | keyword={}", keyword);
                        return new IllegalStateException("KeywordGroup not found after upsert: " + keyword);
                    });

            cache.put(keyword, saved);
            return saved;
        }
    }
}
