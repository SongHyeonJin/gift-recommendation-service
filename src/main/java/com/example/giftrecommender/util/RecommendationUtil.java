package com.example.giftrecommender.util;

import java.util.ArrayList;
import java.util.List;

public class RecommendationUtil {

    // 브랜드 추출
    public static String extractBrand(String title, String mallName) {
        String lower = title.toLowerCase();
        if (lower.contains("삼성")|| lower.contains("samsung")) return "삼성";
        if (lower.contains("apple") || lower.contains("애플")) return "애플";
        if (lower.contains("sony") || lower.contains("소니")) return "소니";
        if (lower.contains("lg") || lower.contains("엘지")) return "LG";
        return mallName;
    }

    /*
     * receiver(예: “여자친구”)
     * reason(예: “생일”, “기념일”)
     * tags(예: “우아한”, “악세서리”, “목걸이”)
     * 1) [tags(r개) + receiver + reason]
     * 2) [tags(r개) + receiver]
     * 3) [tags(r개) + reason]
     * 4) [tags(r개)]
     * 순서로 내림차순(r = tags.size() → 2) 조합 생성
     */
    public static List<List<String>> generatePriorityCombos(List<String> tags, String receiver, String reason) {
        List<List<String>> result = new ArrayList<>();
        int n = tags.size();

        // 태그 조합 (r개씩 - 가장 구체적인 것부터)
        for (int r = n; r >= 2; r--) {
            comboRec(tags, 0, r, new ArrayList<>(), result, receiver, reason);
        }

        // 태그가 1개뿐인 경우도 보장 (예외적 상황)
        if (n == 1) {
            comboRec(tags, 0, 1, new ArrayList<>(), result, receiver, reason);
        }

        // 마지막 fallback: 대상자 + 이유 (태그 없이)
        if (tags.size() <= 1 && !receiver.isBlank() && !reason.isBlank()) {
            result.add(List.of(receiver, reason));
        }

        return result;
    }

    private static void comboRec(List<String> tags, int start, int r, List<String> cur,
                          List<List<String>> out, String receiver, String reason) {
        if (cur.size() == r) {
            // 1) 태그 + 대상자 + 이유
            if (!receiver.isBlank() && !reason.isBlank()) {
                List<String> combo = new ArrayList<>(cur);
                combo.add(receiver);
                combo.add(reason);
                out.add(combo);
            }

            // 2) 태그 + 대상자
            if (!receiver.isBlank()) {
                List<String> combo = new ArrayList<>(cur);
                combo.add(receiver);
                out.add(combo);
            }

            // 3) 태그 + 이유
            if (!reason.isBlank()) {
                List<String> combo = new ArrayList<>(cur);
                combo.add(reason);
                out.add(combo);
            }

            // 4) 태그만
            out.add(new ArrayList<>(cur));
            return;
        }

        for (int i = start; i < tags.size(); i++) {
            cur.add(tags.get(i));
            comboRec(tags, i + 1, r, cur, out, receiver, reason);
            cur.remove(cur.size() - 1);
        }
    }

    public static String extractBaseTitle(String title) {
        return title.replaceAll("\\(.*?\\)", "") // 괄호 제거
                .replaceAll("\\d+(호|mm|ml|g|cm|개)?", "") // 사이즈/숫자 패턴 제거
                .replaceAll("[^가-힣a-zA-Z0-9 ]", "") // 특수문자 제거
                .trim()
                .toLowerCase();
    }
}
