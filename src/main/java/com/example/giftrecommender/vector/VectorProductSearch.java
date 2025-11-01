package com.example.giftrecommender.vector;

import java.util.List;

public interface VectorProductSearch {
    record ScoredId(long productId, double score) {}

    /**
     * 키워드/문장 쿼리를 벡터화하여 Qdrant에서 유사 상품을 점수와 함께 검색
     * @param query         자연어 쿼리 (예: "운동화")
     * @param minPrice      최소 가격
     * @param maxPrice      최대 가격
     * @param age           예: "TEEN" (없으면 null)
     * @param gender        예: "MALE" (없으면 null)
     * @param topK          최대 반환 개수 (여유있게 50~100 추천)
     * @param threshold     유사도 임계값 (예: 0.78)
     */
    List<ScoredId> searchWithScores(String query,
                                    int minPrice, int maxPrice,
                                    String age, String gender,
                                    int topK, double threshold);
}
