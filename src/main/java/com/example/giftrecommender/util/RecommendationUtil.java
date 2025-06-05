package com.example.giftrecommender.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RecommendationUtil {
    /*
     * 추천 조합 우선순위 생성 로직
     *
     * 입력 예:
     * - receiver: "여자친구"
     * - reason: "생일"
     * - tags: ["우아한", "악세서리", "목걸이"]
     *
     * 조합 생성 순서 (우선순위 높은 순):
     * 1) [receiver + tag 1개 + reason]
     * 2) [receiver + tag 2개 + reason]
     * ...
     * n) [receiver + tag n개 + reason]
     * 마지막) [receiver + reason] (태그가 없을 때 fallback)
     *
     * 각 조합은 DB 키워드 검색 등에 사용됨
     */
    public static List<List<String>> generatePriorityCombos(List<String> tags, String receiver, String reason) {
        List<List<String>> result = new ArrayList<>();

        int n = tags.size();
        // 1개부터 n개까지 조합
        for (int r = 1; r <= n; r++) {
            List<List<String>> combinations = new ArrayList<>();
            generateCombinations(tags, 0, r, new ArrayList<>(), combinations);
            for (List<String> tagCombo : combinations) {
                List<String> combo = new ArrayList<>();
                if (!receiver.isBlank()) combo.add(receiver);
                combo.addAll(tagCombo);
                if (!reason.isBlank()) combo.add(reason);
                result.add(combo);
            }
        }

        // 태그 없고 receiver, reason만 있을 때 fallback
        if (tags.isEmpty() && !receiver.isBlank() && !reason.isBlank()) {
            result.add(List.of(receiver, reason));
        }

        return result;
    }

    // 조합 생성
    private static void generateCombinations(List<String> tags, int start, int r,
                                             List<String> cur, List<List<String>> out) {
        if (cur.size() == r) {
            out.add(new ArrayList<>(cur));
            return;
        }
        for (int i = start; i < tags.size(); i++) {
            cur.add(tags.get(i));
            generateCombinations(tags, i + 1, r, cur, out);
            cur.remove(cur.size() - 1);
        }
    }

    /*
     * 상품 제목 전처리 로직
     * - 불필요한 특수 문자, 단위, 괄호 등 제거
     * - 유사한 상품 이름 비교를 위한 정규화 처리
     */
    public static String extractBaseTitle(String title) {
        return title
                .replaceAll("\\[[^\\]]*\\]", "") // 대괄호 제거: [정품], [BTS 에디션]
                .replaceAll("\\([^)]*\\)", "") // 괄호 제거: (화이트)
                .replaceAll("[-_•·|]", " ") // 특수구분자 -> 공백
                .replaceAll("\\d+(호|mm|ml|g|kg|cm|개|세트|pack|팩)?", "") // 단위 포함 숫자 제거
                .replaceAll("[^가-힣a-zA-Z0-9 ]", "") // 나머지 특수문자 제거
                .replaceAll("\\s{2,}", " ") // 다중 공백 제거
                .trim()
                .toLowerCase();
    }

    /*
     * Jaccard 유사도 측정 (단어 기준)
     * - 상품 제목을 단어 단위로 나누어 유사도 측정
     * - 유사도 = 교집합 크기 / 합집합 크기
     * - 유사한 상품 추천 시 중복 판단에 사용
     */
    public static double jaccardSimilarityByWords(String title1, String title2) {
        Set<String> words1 = new HashSet<>(List.of(title1.split("\\s+")));
        Set<String> words2 = new HashSet<>(List.of(title2.split("\\s+")));

        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);

        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }
}
