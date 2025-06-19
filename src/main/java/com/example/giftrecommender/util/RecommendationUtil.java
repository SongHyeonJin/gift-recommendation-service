package com.example.giftrecommender.util;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RecommendationUtil {
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
    /*
     * 브랜드 추출
     * - DB에 저장된 brand 컬럼 값 기반으로 사용
     * - 빈 문자열이거나 null인 경우 중복 제거 대상에서 제외
     */
    public static String extractBrand(String brand) {
        if (brand == null || brand.isBlank()) return null;
        return brand.trim().toLowerCase();
    }

    public static boolean isBabyKeywordIncluded(String title) {
        List<String> babyKeywords = List.of(
                "아기", "유아", "아동", "키즈", "어린이", "아이", "장난감", "초등", "유치원", "베이비", "소꿉놀이", "뽀로로"
        );
        String lowerTitle = title.toLowerCase();
        return babyKeywords.stream().anyMatch(lowerTitle::contains);
    }

    public static boolean allowBabyProduct(String title, String age, String reason, String preference) {
        return !isBabyKeywordIncluded(title) || "10대 미만".equals(age) || "출산".equals(reason) || "출산/육아".equals(preference);
    }

}
