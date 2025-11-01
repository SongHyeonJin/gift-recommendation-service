package com.example.giftrecommender.util;

import com.example.giftrecommender.domain.entity.CrawlingProduct;
import com.example.giftrecommender.domain.enums.Gender;
import org.jsoup.Jsoup;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class RecommendationUtil {
    // 정규식/공용 리소스
    private static final Pattern PUNCT_OR_BRACKETS = Pattern.compile("[\\[\\]\\(\\)\\{\\}<>]|[\\p{Punct}&&[^/#]]");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s{2,}");
    private static final Pattern NON_KO_EN_NUM = Pattern.compile("[^\\p{IsHangul}\\p{Alnum}\\s/#]");
    private static final Pattern UNIT_NUMBER = Pattern.compile("\\b\\d+(호|mm|ml|g|kg|cm|개|세트|pack|팩)?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORD_KEEP = Pattern.compile("[^\\p{IsHangul}\\p{Alnum}\\s]");
    private static final Pattern WORD_SPLIT = Pattern.compile("\\s+");
    private static final Pattern SPLIT_DELIMS = Pattern.compile("[\\s/|,]+");
    private static final Pattern HANGUL_RUN = Pattern.compile("[\\p{IsHangul}]{2,}");
    private static final Pattern EN_NUM_RUN = Pattern.compile("[A-Za-z0-9]{2,}");

    // 간단 불용어(필요 시 확장 권장)
    private static final Set<String> STOPWORDS = Set.of(
            "the","and","for","with","from","into","over","under","of","in","on","to","by",
            "무료","무료배송","정품","공식","신형","국내","해외","배송","빠른",
            "오늘","내일","인기","추천","사은품","증정","세트","패키지","행사",
            "사이즈","색상","옵션","종류","타입","모델","호환",
            "남성","여성","남자","여자","커플","학생","성인","어린이",
            "사용","용","형","형식"
    );

    // 영유아 컷용 키워드
    private static final Set<String> BABY_NEGATIVE = Set.of(
            "아기","유아","영유아","신생아","키즈","어린이","아이",
            "기저귀","분유","젖병","노리개","턱받이","유모차","아기띠","보행기",
            "유아식","이유식","젖꼭지","치발기"
    );

    // 성별 필터용 키워드
    private static final Set<String> FEMALE_WORDS = Set.of(
            "여성", "여자", "레이디", "lady", "female", "여성용", "여성 선물", "여자선물"
    );

    private static final Set<String> MALE_WORDS = Set.of(
            "남성", "남자", "man ", "men ", "male", "남성용", "남자선물", "남성 선물"
    );

    private RecommendationUtil() {
    }

    // 1. 제목 전처리
    public static String extractBaseTitle(String title) {
        if (title == null) return "";

        String s = Jsoup.parse(title).text();
        s = Normalizer.normalize(s, Normalizer.Form.NFC);
        s = PUNCT_OR_BRACKETS.matcher(s).replaceAll(" ");
        s = UNIT_NUMBER.matcher(s).replaceAll(" ");
        s = NON_KO_EN_NUM.matcher(s).replaceAll(" ");
        s = MULTI_SPACE.matcher(s).replaceAll(" ").trim();
        return s.toLowerCase(Locale.ROOT);
    }

    // 2. 자카드 유사도(단어 기반)
    public static double jaccardSimilarityByWords(String title1, String title2) {
        if (title1 == null || title2 == null) return 0.0;

        String a = WORD_KEEP.matcher(title1).replaceAll(" ");
        String b = WORD_KEEP.matcher(title2).replaceAll(" ");

        Set<String> s1 = Arrays.stream(WORD_SPLIT.split(a.trim()))
                .map(x -> x.toLowerCase(Locale.ROOT))
                .filter(x -> x.length() >= 2 && !STOPWORDS.contains(x))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> s2 = Arrays.stream(WORD_SPLIT.split(b.trim()))
                .map(x -> x.toLowerCase(Locale.ROOT))
                .filter(x -> x.length() >= 2 && !STOPWORDS.contains(x))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (s1.isEmpty() && s2.isEmpty()) return 1.0;
        if (s1.isEmpty() || s2.isEmpty())  return 0.0;

        Set<String> inter = new HashSet<>(s1);
        inter.retainAll(s2);

        Set<String> union = new HashSet<>(s1);
        union.addAll(s2);

        return union.isEmpty() ? 0.0 : (double) inter.size() / (double) union.size();
    }

    // 3. 브랜드 정규화
    public static String extractBrand(String brand) {
        if (brand == null) return "";
        String b = Normalizer.normalize(brand, Normalizer.Form.NFC).trim().toLowerCase(Locale.ROOT);
        b = b.replaceAll("[\\s_]+", " ");
        return b;
    }

    // 4. 영유아/선호 필터
    public static boolean isBabyKeywordIncluded(String title) {
        if (title == null) return false;
        String lower = title.toLowerCase(Locale.ROOT);
        for (String k : BABY_NEGATIVE) {
            if (lower.contains(k.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    public static boolean allowBabyProduct(String title, String age, String reason, String preference) {
        boolean babyInTitle = isBabyKeywordIncluded(title);
        boolean adultAge = containsAnyIgnoreCase(age, "10대", "20대", "30대", "40대", "50대", "성인");
        boolean babyContext = containsAnyIgnoreCase(reason, "출산", "돌잔치", "백일", "아기", "유아", "신생아")
                || containsAnyIgnoreCase(preference, "출산", "육아", "유아", "영유아", "키즈", "베이비");

        if (babyInTitle && adultAge && !babyContext) return false;
        return true;
    }

    private static boolean containsAnyIgnoreCase(String src, String... needles) {
        if (src == null || src.isBlank()) return false;
        String s = src.toLowerCase(Locale.ROOT);
        for (String n : needles) {
            if (n != null && !n.isBlank() && s.contains(n.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    // 5. 제목에서 간단 명사 추출
    public static List<String> naiveKoreanNouns(String title) {
        if (title == null || title.isBlank()) return List.of();

        String s = Normalizer.normalize(title, Normalizer.Form.NFC);
        s = PUNCT_OR_BRACKETS.matcher(s).replaceAll(" ");
        s = NON_KO_EN_NUM.matcher(s).replaceAll(" ");
        s = MULTI_SPACE.matcher(s).replaceAll(" ").trim();
        if (s.isBlank()) return List.of();

        String[] first = SPLIT_DELIMS.split(s);
        LinkedHashSet<String> out = new LinkedHashSet<>();

        for (String rt : first) {
            if (rt == null) continue;
            String token = rt.trim();
            if (token.isEmpty()) continue;

            List<String> pieces = new ArrayList<>();

            Matcher m1 = HANGUL_RUN.matcher(token);
            while (m1.find()) {
                pieces.add(m1.group());
            }

            Matcher m2 = EN_NUM_RUN.matcher(token);
            while (m2.find()) {
                pieces.add(m2.group());
            }

            for (String p : pieces) {
                String cand = p.trim();
                if (cand.length() < 2) continue;

                String lower = cand.toLowerCase(Locale.ROOT);
                if (STOPWORDS.contains(lower)) continue;

                cand = cand.replaceAll("(세트|패키지|증정|정품|공식)$", "").trim();
                if (cand.length() < 2) continue;

                out.add(cand);
            }
        }

        return out.stream()
                .filter(t -> t.length() <= 20)
                .limit(20)
                .collect(Collectors.toList());
    }

    public static int calculateScore(BigDecimal rating, Integer reviewCount) {
        int score = 0;
        if (rating != null && rating.compareTo(BigDecimal.valueOf(4.2)) >= 0) score += 1;
        if (reviewCount != null && reviewCount >= 100) score += 1;
        if (reviewCount != null && reviewCount >= 1000 && rating != null && rating.compareTo(BigDecimal.valueOf(4.5)) >= 0) score += 1;
        if (reviewCount != null && reviewCount >= 10000 && rating != null && rating.compareTo(BigDecimal.valueOf(4.3)) >= 0) score += 1;
        return score;
    }

    public static String generateDisplayName(String originalName) {
        if (originalName == null) return null;
        String name = originalName;

        name = name.replaceAll("\\[.*?\\]", "")
                .replaceAll("\\(.*?\\)", "")
                .replaceAll("\\{.*?\\}", "");

        name = name.replaceAll("[★♥●◆◎※]", "");

        String[] removeKeywords = {
                "무료배송", "빠른배송", "사은품", "당일발송",
                "세트", "세트상품", "1\\+1", "2\\+1", "3\\+1",
                "인기", "추천", "HOT", "Best", "BEST", "신상품"
        };
        for (String keyword : removeKeywords) {
            name = name.replaceAll("(?i)" + keyword, "");
        }

        name = name.trim().replaceAll("\\s{2,}", " ");
        return name;
    }

    /**
     * CrawlingProduct + Gender 기반 성별 필터
     * - requestGender == MALE 이고 상품이 여성용이면 차단
     * - requestGender == FEMALE 이고 상품이 남성용이면 차단
     */
    public static boolean blockedByGender(Gender requestGender, CrawlingProduct product) {
        if (requestGender == null || product == null) {
            return false;
        }

        String title = product.getDisplayName();
        if (title == null || title.isBlank()) {
            title = product.getOriginalName();
        }
        String lowerTitle = title != null ? title.toLowerCase(Locale.ROOT) : "";

        List<String> tags = product.getKeywords() != null ? product.getKeywords() : List.of();
        String tagsJoined = String.join(" ", tags).toLowerCase(Locale.ROOT);

        boolean isFemaleItem = containsAny(lowerTitle, FEMALE_WORDS) || containsAny(tagsJoined, FEMALE_WORDS);
        boolean isMaleItem = containsAny(lowerTitle, MALE_WORDS) || containsAny(tagsJoined, MALE_WORDS);

        if (requestGender == Gender.MALE && isFemaleItem) {
            return true;
        }
        if (requestGender == Gender.FEMALE && isMaleItem) {
            return true;
        }
        return false;
    }

    private static boolean containsAny(String text, Set<String> words) {
        for (String w : words) {
            if (text.contains(w.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
