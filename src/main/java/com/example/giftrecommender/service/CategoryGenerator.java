package com.example.giftrecommender.service;

import com.example.giftrecommender.domain.entity.CrawlingProduct;
import com.openai.client.OpenAIClient;
import com.openai.errors.RateLimitException;
import com.openai.models.ChatModel;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "openai", name = "enabled", havingValue = "true")
public class CategoryGenerator {

    public enum Action { SKIP, NORMALIZE_ONLY, FILL_EMPTY, REPLACE_BAD }
    public enum Mode { CONSERVATIVE, VALIDATE }

    public record Decision(Action action, String category, String raw, String reason) {}

    private record GptResult(String raw, String normalized) {}

    private static final ChatModel MODEL = ChatModel.GPT_4_1_MINI;

    private final OpenAIClient client;
    private final boolean enabled;
    private final Mode mode;

    // ✅ 도메인 축 강충돌 가드
    private final boolean mismatchGuardEnabled;

    // ✅ “현재 카테고리가 제목/키워드에 비해 어색하면 고치기” 가드
    private final boolean semanticGuardEnabled;

    // ✅ mismatch 원인 추적용(원하면 yml에서 끄기)
    private final boolean mismatchDebugLogEnabled;

    private static final String SEP = ">";

    private static final List<String> BAD_TOKENS = List.of(
            "기타", "미분류", "일반", "unknown", "etc", "none", "null", "없음", "미정"
    );

    private static final Pattern MULTI_LINE = Pattern.compile("[\\r\\n]+");
    private static final Pattern QUOTES = Pattern.compile("^[\"']|[\"']$");

    // ✅ semantic-check 응답 파싱용
    private static final Pattern FIX_PREFIX = Pattern.compile("^\\s*FIX\\s*:\\s*", Pattern.CASE_INSENSITIVE);

    public CategoryGenerator(
            OpenAIClient client,
            @Value("${openai.enabled:true}") boolean enabled,
            @Value("${backfill.category.mode:VALIDATE}") String mode,
            @Value("${backfill.category.mismatch-guard:true}") boolean mismatchGuardEnabled,
            @Value("${backfill.category.semantic-guard:true}") boolean semanticGuardEnabled,
            @Value("${backfill.category.mismatch-debug-log:false}") boolean mismatchDebugLogEnabled
    ) {
        this.client = client;
        this.enabled = enabled;
        this.mode = Mode.valueOf(mode.trim().toUpperCase(Locale.ROOT));
        this.mismatchGuardEnabled = mismatchGuardEnabled;
        this.semanticGuardEnabled = semanticGuardEnabled;
        this.mismatchDebugLogEnabled = mismatchDebugLogEnabled;
    }

    public Decision decideAndGenerate(CrawlingProduct p) {
        String title = safe(p.getDisplayName(), p.getOriginalName());
        String current = safe(p.getCategory());
        List<String> keywords = p.getKeywords() == null ? List.of() : p.getKeywords();
        return decideAndGenerate(title, keywords, current);
    }

    public Decision decideAndGenerate(String title, List<String> keywords, String currentCategory) {
        String safeTitle = safe(title);
        List<String> safeKeywords = (keywords == null) ? List.of() : keywords;
        String current = safe(currentCategory);

        // openai 꺼져있으면 최소 normalize만
        if (!enabled) {
            if (!hasText(current)) {
                return new Decision(Action.SKIP, null, null, "openai.enabled=false (cannot fill empty)");
            }
            String normalized = normalizeToTwoLevel(current);
            if (hasText(normalized) && !normalized.equals(current)) {
                return new Decision(Action.NORMALIZE_ONLY, normalized, normalized, "normalize only (openai disabled)");
            }
            return new Decision(Action.SKIP, null, null, "openai.enabled=false (no change)");
        }

        // 1) 비어있으면 생성
        if (!hasText(current)) {
            GptResult r = generateCategoryByGpt(safeTitle, safeKeywords, null, "카테고리가 비어있음", false);
            if (!hasText(r.normalized)) {
                return new Decision(Action.SKIP, null, r.raw, "fill failed (normalize null)");
            }
            return new Decision(Action.FILL_EMPTY, r.normalized, r.raw, "filled empty");
        }

        // 2) 형식 normalize (GPT 호출 없이)
        String normalizedCurrent = normalizeToTwoLevel(current);
        if (hasText(normalizedCurrent) && !normalizedCurrent.equals(current)) {
            return new Decision(Action.NORMALIZE_ONLY, normalizedCurrent, normalizedCurrent, "normalized format");
        }

        // 3) 현재 값이 이상(placeholder/형식불량)하면 교정
        if (!hasText(normalizedCurrent) || isBadCategory(normalizedCurrent)) {
            GptResult r = generateCategoryByGpt(
                    safeTitle, safeKeywords, current,
                    "현재 카테고리가 형식/내용 기준을 만족하지 않음", true
            );
            if (!hasText(r.normalized) || isBadCategory(r.normalized)) {
                return new Decision(Action.SKIP, null, r.raw, "replace failed (invalid result)");
            }
            return new Decision(Action.REPLACE_BAD, r.normalized, r.raw, "replaced bad current");
        }

        // 3.5) 도메인 축 강충돌(확실한 오분류)만 교정
        if (mismatchGuardEnabled && looksMismatchedWithProduct(normalizedCurrent, safeTitle, safeKeywords)) {
            GptResult r = generateCategoryByGpt(
                    safeTitle, safeKeywords, normalizedCurrent,
                    "현재 카테고리가 상품 정보(상품명/키워드)와 도메인 축이 강하게 불일치하여 교정", false
            );
            if (!hasText(r.normalized) || isBadCategory(r.normalized)) {
                return new Decision(Action.SKIP, null, r.raw, "mismatch replace failed (invalid result)");
            }
            if (r.normalized.equals(normalizedCurrent)) {
                return new Decision(Action.SKIP, null, r.raw, "mismatch check says same");
            }
            return new Decision(Action.REPLACE_BAD, r.normalized, r.raw, "mismatch corrected");
        }

        // 3.6) “형식도 OK, 도메인도 애매하지 않지만” 제목/키워드와 의미가 어색하면 교정
        // - GPT에게 'OK' 또는 'FIX:대>중'으로만 답하게 해서 오탐/장문을 줄임
        if (semanticGuardEnabled) {
            String fixed = semanticValidateAndFix(safeTitle, safeKeywords, normalizedCurrent);
            if (hasText(fixed) && !fixed.equals(normalizedCurrent)) {
                return new Decision(Action.REPLACE_BAD, fixed, fixed, "semantic guard corrected");
            }
        }

        // 4) VALIDATE 모드면 검증/교정 (기존대로)
        if (mode == Mode.VALIDATE) {
            GptResult r = generateCategoryByGpt(
                    safeTitle, safeKeywords, normalizedCurrent,
                    "현재 카테고리 검증/교정", true
            );
            if (!hasText(r.normalized) || isBadCategory(r.normalized)) {
                return new Decision(Action.SKIP, null, r.raw, "validate failed (invalid result)");
            }
            if (r.normalized.equals(normalizedCurrent)) {
                return new Decision(Action.SKIP, null, r.raw, "validate says same");
            }
            return new Decision(Action.REPLACE_BAD, r.normalized, r.raw, "validate changed category");
        }

        return new Decision(Action.SKIP, null, null, "conservative skip (current looks ok)");
    }

    /* ======================================================
       ✅ Semantic guard
       ====================================================== */

    /**
     * GPT에게 “현재 카테고리가 제목/키워드와 잘 맞는지”만 판정시킨다.
     * - OK 또는 FIX:대>중 만 허용
     * - FIX일 때만 normalize 후 반영
     */
    private String semanticValidateAndFix(String title, List<String> keywords, String currentCategory) {
        String prompt = buildSemanticCheckPrompt(title, keywords, currentCategory);

        String raw = callGptWithRetry(prompt, title, 2);
        raw = normalizeRawLine(raw); // 한줄/잡문 제거
        if (!hasText(raw)) return null;

        if ("OK".equalsIgnoreCase(raw.trim())) return null;

        String candidate = FIX_PREFIX.matcher(raw).replaceFirst("").trim();
        candidate = candidate.replaceFirst("^[-–—]\\s*", "").trim();

        String normalized = normalizeToTwoLevel(candidate);
        if (!hasText(normalized) || isBadCategory(normalized)) return null;

        return normalized;
    }

    private String buildSemanticCheckPrompt(String title, List<String> keywords, String currentCategory) {
        StringBuilder sb = new StringBuilder();
        sb.append("너는 쇼핑몰 상품 카테고리 품질 검사기야.\n");
        sb.append("아래 상품명/키워드와 현재 카테고리를 보고, 현재 카테고리가 적절한지 판단해.\n\n");

        sb.append("[입력]\n");
        sb.append("- 상품명: ").append(title).append("\n");
        sb.append("- 키워드: ").append(keywords).append("\n");
        sb.append("- 현재 카테고리: ").append(currentCategory).append("\n\n");

        sb.append("[출력 규칙 - 엄격]\n");
        sb.append("1) 결과는 반드시 한 줄\n");
        sb.append("2) 적절하면 'OK'만 출력\n");
        sb.append("3) 부적절하면 'FIX:대분류>중분류' 형식으로만 출력\n");
        sb.append("4) 설명/이유/따옴표/마크다운/라벨/부가문장 금지\n");
        sb.append("5) '기타/미분류/일반/unknown/etc/none/null/없음/미정' 같은 placeholder 금지\n");
        sb.append("6) 중분류를 너무 구체적인 품목명(예: 텐트/버너 등)으로 만들지 말고, 쇼핑몰에서 흔히 쓰는 중분류로\n");

        return sb.toString();
    }

    /* ======================================================
       GPT 호출 + 재시도 + 결과 검증
       ====================================================== */

    private GptResult generateCategoryByGpt(
            String title,
            List<String> keywords,
            String currentCategoryOrNull,
            String reason,
            boolean allowSameAsCurrent
    ) {
        String prompt = buildPrompt(title, keywords, currentCategoryOrNull, reason, allowSameAsCurrent);

        String raw1 = callGptWithRetry(prompt, title, 3);
        String normalized1 = normalizeToTwoLevel(raw1);

        if (isAcceptable(normalized1)) {
            return new GptResult(raw1, normalized1);
        }

        String prompt2 = buildRetryPrompt(title, keywords, currentCategoryOrNull, reason, raw1, allowSameAsCurrent);
        String raw2 = callGptWithRetry(prompt2, title, 2);
        String normalized2 = normalizeToTwoLevel(raw2);

        return new GptResult(raw2, normalized2);
    }

    private boolean isAcceptable(String normalized) {
        return hasText(normalized) && !isBadCategory(normalized);
    }

    private String callGptWithRetry(String prompt, String titleForLog, int maxRetries) {
        Duration backoff = Duration.ofMillis(500);

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.debug("[CATEGORY][CALL] model={}, attempt={}, title='{}'", MODEL, attempt, safeHead(titleForLog));

                ResponseCreateParams params = ResponseCreateParams.builder()
                        .model(MODEL)
                        .input(prompt)
                        .maxOutputTokens(40)
                        .temperature(0.1)
                        .build();

                Response response = client.responses().create(params);

                String extracted = extractOutputTextCompat(response);
                String raw = normalizeRawLine(extracted);

                if (!hasText(raw)) {
                    log.warn("[CATEGORY][EMPTY] title='{}', extracted='{}', rawResponse={}",
                            safeHead(titleForLog), extracted, response);
                    throw new IllegalStateException("모델 응답이 비어있음");
                }

                return raw;

            } catch (RateLimitException e) {
                log.warn("[CATEGORY][RETRY] RateLimit attempt={}/{}, backoff={}ms, msg={}",
                        attempt, maxRetries, backoff.toMillis(), e.getMessage());

            } catch (Exception e) {
                log.warn("[CATEGORY][RETRY] {} attempt={}/{}, backoff={}ms, msg={}",
                        isTimeoutLike(e) ? "TimeoutLike" : e.getClass().getSimpleName(),
                        attempt, maxRetries, backoff.toMillis(), e.getMessage(), e);
            }

            sleep(backoff);
            backoff = backoff.multipliedBy(2);
        }

        return null;
    }

    private String normalizeRawLine(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        if (trimmed.isEmpty()) return null;

        trimmed = trimmed.replace("\n", " ").replace("\r", " ").replaceAll("\\s+", " ").trim();
        trimmed = trimmed.replace("카테고리:", "").trim();

        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
                (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }

        if (trimmed.startsWith("ResponseOutput") || trimmed.startsWith("ResponseOutputMessage")) {
            return null;
        }

        if (trimmed.length() > 80) trimmed = trimmed.substring(0, 80).trim();

        return trimmed;
    }

    /* ======================================================
       Prompt
       ====================================================== */

    private String buildPrompt(String title,
                               List<String> keywords,
                               String currentCategoryOrNull,
                               String reason,
                               boolean allowSameAsCurrent) {

        StringBuilder sb = new StringBuilder();
        sb.append("너는 쇼핑몰 상품을 2단계 카테고리로 분류하는 분류기야.\n");
        sb.append("아래 상품 정보를 보고 카테고리를 '대분류>중분류' 형식으로 한 줄만 출력해.\n\n");

        sb.append("[상품 정보]\n");
        sb.append("- 상품명: ").append(title).append("\n");
        sb.append("- 키워드: ").append(keywords).append("\n");
        if (hasText(currentCategoryOrNull)) {
            sb.append("- 현재 카테고리: ").append(currentCategoryOrNull).append("\n");
        }
        sb.append("- 작업 사유: ").append(safe(reason)).append("\n\n");

        sb.append("[출력 규칙]\n");
        sb.append("1) 반드시 한 줄, '대분류>중분류' 형식만 출력\n");
        sb.append("2) 설명/부가문장/따옴표/마크다운/라벨(예: 카테고리:) 금지\n");
        sb.append("3) '기타/미분류/일반/unknown/etc/none/null/없음/미정' 같은 placeholder 금지\n");
        sb.append("4) 가능한 한 일반적인 쇼핑몰 분류 기준으로 선택\n");
        if (hasText(currentCategoryOrNull) && allowSameAsCurrent) {
            sb.append("5) 현재 카테고리가 적절하면 그대로 출력해도 됨\n");
        }
        return sb.toString();
    }

    private String buildRetryPrompt(String title,
                                    List<String> keywords,
                                    String currentCategoryOrNull,
                                    String reason,
                                    String previousRaw,
                                    boolean allowSameAsCurrent) {

        StringBuilder sb = new StringBuilder();
        sb.append("이전 출력이 규칙을 위반했거나 비어있었어. 규칙을 반드시 지켜서 다시 출력해.\n\n");

        sb.append("[상품 정보]\n");
        sb.append("- 상품명: ").append(title).append("\n");
        sb.append("- 키워드: ").append(keywords).append("\n");
        if (hasText(currentCategoryOrNull)) {
            sb.append("- 현재 카테고리: ").append(currentCategoryOrNull).append("\n");
        }
        sb.append("- 작업 사유: ").append(safe(reason)).append("\n\n");

        sb.append("[이전 출력(참고)]\n");
        sb.append(safe(previousRaw)).append("\n\n");

        sb.append("[출력 규칙 - 반드시]\n");
        sb.append("- 오직 한 줄\n");
        sb.append("- 오직 '대분류>중분류' 형식\n");
        sb.append("- 설명/부가문장/따옴표/마크다운/라벨 금지\n");
        sb.append("- placeholder 금지\n");
        if (hasText(currentCategoryOrNull) && allowSameAsCurrent) {
            sb.append("- 현재 카테고리가 적절하면 그대로 출력 가능\n");
        }
        return sb.toString();
    }

    /* ======================================================
       Category validation/normalize
       ====================================================== */

    private boolean isBadCategory(String category) {
        if (!hasText(category)) return true;

        String c = category.trim();
        if (!c.contains(SEP)) return true;

        String[] parts = c.split(Pattern.quote(SEP));
        if (parts.length < 2) return true;

        String a = parts[0].trim();
        String b = parts[1].trim();
        if (!hasText(a) || !hasText(b)) return true;

        String lower = c.toLowerCase(Locale.ROOT);
        for (String bad : BAD_TOKENS) {
            if (lower.contains(bad.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    public String normalizeToTwoLevel(String raw) {
        if (!hasText(raw)) return null;

        String s = raw.trim();
        s = MULTI_LINE.split(s)[0].trim();
        s = QUOTES.matcher(s).replaceAll("").trim();

        s = s.replace("\\", "/")
                .replace("|", "/")
                .replace("»", "/")
                .replace("→", "/")
                .replace("-", "/")
                .replace("—", "/");

        s = s.replace(">", "/");
        s = s.replaceAll("\\s+", " ");
        s = s.replaceAll("\\s*/\\s*", "/").trim();

        String[] parts = s.split("/");
        if (parts.length < 2) return null;

        String a = parts[0].trim();
        String b = parts[1].trim();
        if (!hasText(a) || !hasText(b)) return null;

        String lower = (a + " " + b).toLowerCase(Locale.ROOT);
        for (String bad : BAD_TOKENS) {
            if (lower.contains(bad.toLowerCase(Locale.ROOT))) return null;
        }

        return a + SEP + b;
    }

    /* ======================================================
       ✅ Mismatch guard (도메인 축 기반)
       ====================================================== */

    private enum Domain {
        DIGITAL, FASHION, FOOD, BEAUTY, SPORTS, LIVING, KITCHEN, STATIONERY,
        PET, AUTO, TOOL, BOOK, UNKNOWN
    }

    private boolean looksMismatchedWithProduct(String category, String title, List<String> keywords) {
        if (!hasText(category)) return true;

        String blob = (safe(title) + " " + String.join(" ", keywords == null ? List.of() : keywords))
                .toLowerCase(Locale.ROOT);

        Domain inferred = inferDomainFromText(blob);
        Domain catDomain = inferDomainFromCategory(category);

        if (mismatchDebugLogEnabled) {
            log.info("[CATEGORY][MISMATCH_DEBUG] inferred={}, catDomain={}, title='{}', cat='{}', blob='{}'",
                    inferred, catDomain, safeHead(title), category, safeHead(blob));
        }

        if (inferred == Domain.UNKNOWN || catDomain == Domain.UNKNOWN) return false;
        return isHardConflict(inferred, catDomain);
    }

    private Domain inferDomainFromCategory(String category) {
        String c = category.toLowerCase(Locale.ROOT);

        if (containsAny(c, "디지털", "pc", "모바일", "오디오", "가전", "영상", "촬영")) return Domain.DIGITAL;
        if (containsAny(c, "패션", "의류", "잡화", "액세서리", "가방", "지갑", "신발")) return Domain.FASHION;
        if (containsAny(c, "식품", "건강식품", "가공식품", "커피", "차")) return Domain.FOOD;
        if (containsAny(c, "뷰티", "향", "디퓨저", "바디", "헤어", "스킨")) return Domain.BEAUTY;
        if (containsAny(c, "스포츠", "레저", "운동", "캠핑", "여행")) return Domain.SPORTS;
        if (containsAny(c, "생활", "인테리어", "조명", "수납", "욕실", "청소", "생활용품")) return Domain.LIVING;
        if (containsAny(c, "주방", "식기", "그릇", "텀블러", "컵", "조리", "용기")) return Domain.KITCHEN;
        if (containsAny(c, "문구", "오피스", "사무", "필기")) return Domain.STATIONERY;
        if (containsAny(c, "반려", "펫")) return Domain.PET;
        if (containsAny(c, "자동차", "차량")) return Domain.AUTO;
        if (containsAny(c, "공구", "산업")) return Domain.TOOL;
        if (containsAny(c, "도서", "책")) return Domain.BOOK;

        return Domain.UNKNOWN;
    }

    private Domain inferDomainFromText(String blob) {
        // ✅ (핵심 추가) 액막이/개업/집들이/행운/풍수/장식/소품 계열은 FOOD로 착각하기 쉬우니
        //    무조건 LIVING을 우선으로 잡아준다.
        if (containsAny(blob,
                "액막이", "개업", "집들이", "행운", "풍수",
                "장식", "인테리어", "소품", "선물용", "벽걸이", "걸이", "장식품", "포인트")) {
            return Domain.LIVING;
        }

        // 기존 룰
        if (containsAny(blob, "블루투스", "무선", "usb", "c타입", "충전", "이어폰", "헤드", "스피커", "마이크",
                "키보드", "마우스", "모니터")) {
            return Domain.DIGITAL;
        }
        if (containsAny(blob, "가방", "지갑", "모자", "양말", "장갑", "머플러", "스카프", "벨트",
                "의류", "셔츠", "바지", "후드")) {
            return Domain.FASHION;
        }
        if (containsAny(blob, "식품", "먹", "간식", "과일", "커피", "차", "홍삼", "건강식품", "원두")) {
            return Domain.FOOD;
        }
        if (containsAny(blob, "향", "디퓨저", "향수", "바디", "헤어", "샴푸", "로션", "스킨", "크림")) {
            return Domain.BEAUTY;
        }
        if (containsAny(blob, "운동", "헬스", "요가", "러닝", "등산", "캠핑", "레저", "여행")) {
            return Domain.SPORTS;
        }
        if (containsAny(blob, "조명", "무드등", "스탠드", "램프", "인테리어", "수납", "청소", "욕실", "생활용품")) {
            return Domain.LIVING;
        }
        if (containsAny(blob, "텀블러", "컵", "식기", "그릇", "주방", "조리", "도마", "칼", "용기")) {
            return Domain.KITCHEN;
        }
        if (containsAny(blob, "문구", "노트", "필기", "펜", "다이어리", "사무", "오피스")) {
            return Domain.STATIONERY;
        }
        if (containsAny(blob, "반려", "강아지", "고양이", "펫")) return Domain.PET;
        if (containsAny(blob, "자동차", "차량")) return Domain.AUTO;
        if (containsAny(blob, "공구", "드릴", "렌치")) return Domain.TOOL;
        if (containsAny(blob, "도서", "책", "ebook")) return Domain.BOOK;

        return Domain.UNKNOWN;
    }

    /**
     * ✅ (핵심 변경) FOOD는 “오분류가 치명적”이라
     * catDomain=FOOD 일 때 inferred가 FOOD가 아니면 강충돌로 본다.
     */
    private boolean isHardConflict(Domain inferred, Domain catDomain) {
        // 1) FOOD 카테고리는 엄격하게 일치 요구 (식품>건어물 오분류 같은 것 잡기)
        if (catDomain == Domain.FOOD) {
            // inferred가 FOOD가 아니면 거의 오분류로 보고 교정 트리거
            return inferred != Domain.FOOD;
        }

        // 2) 특정 도메인은 엄격하게 일치 요구 (기존 룰 유지)
        if (inferred == Domain.FOOD) return catDomain != Domain.FOOD;
        if (inferred == Domain.TOOL) return catDomain != Domain.TOOL;
        if (inferred == Domain.AUTO) return catDomain != Domain.AUTO;
        if (inferred == Domain.BOOK) return catDomain != Domain.BOOK;

        if (inferred == Domain.DIGITAL) {
            return (catDomain == Domain.FOOD || catDomain == Domain.TOOL || catDomain == Domain.AUTO || catDomain == Domain.BOOK);
        }
        if (inferred == Domain.FASHION) {
            return (catDomain == Domain.FOOD || catDomain == Domain.TOOL || catDomain == Domain.AUTO || catDomain == Domain.BOOK);
        }
        return false;
    }

    private static boolean containsAny(String text, String... tokens) {
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String t : tokens) {
            if (t != null && !t.isBlank() && lower.contains(t.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    /* ======================================================
       Responses API Text Extractor
       ====================================================== */

    private static String extractOutputTextCompat(Response response) {
        if (response == null) return null;

        Object output = invokeIfExists(response, "output");
        String t1 = extractFromOutputList(output);
        if (hasText(t1)) return t1;

        Object _output = invokeIfExists(response, "_output");
        Object unwrapped = unwrapJsonField(_output);
        String t2 = extractFromOutputList(unwrapped);
        if (hasText(t2)) return t2;

        return null;
    }

    private static String extractFromOutputList(Object maybeList) {
        if (!(maybeList instanceof List<?> list)) return null;

        StringBuilder sb = new StringBuilder();
        for (Object item : list) {
            String t = extractAnyText(item, 0);
            if (hasText(t)) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(t.trim());
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String extractAnyText(Object obj, int depth) {
        if (obj == null) return null;
        if (depth > 7) return null;

        if (obj instanceof Optional<?> opt) {
            return extractAnyText(opt.orElse(null), depth + 1);
        }

        Object jf = unwrapJsonField(obj);
        if (jf != obj) {
            return extractAnyText(jf, depth + 1);
        }

        if (obj instanceof String s) {
            return s;
        }

        if (obj instanceof List<?> list) {
            StringBuilder sb = new StringBuilder();
            for (Object it : list) {
                String t = extractAnyText(it, depth + 1);
                if (hasText(t)) {
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(t.trim());
                }
            }
            return sb.length() == 0 ? null : sb.toString();
        }

        Object content = invokeIfExists(obj, "content");
        if (content != null) {
            String t = extractAnyText(content, depth + 1);
            if (hasText(t)) return t;
        }

        String[] direct = {"text", "inputText", "value", "outputText"};
        for (String m : direct) {
            Object v = invokeIfExists(obj, m);
            String t = extractAnyText(v, depth + 1);
            if (hasText(t)) return t;
        }

        String[] unders = {"_text", "_inputText", "_value", "_outputText", "_content"};
        for (String m : unders) {
            Object v = unwrapJsonField(invokeIfExists(obj, m));
            String t = extractAnyText(v, depth + 1);
            if (hasText(t)) return t;
        }

        Object message = invokeIfExists(obj, "message");
        if (message != null) {
            String t = extractAnyText(message, depth + 1);
            if (hasText(t)) return t;
        }

        Object _message = unwrapJsonField(invokeIfExists(obj, "_message"));
        if (_message != null) {
            String t = extractAnyText(_message, depth + 1);
            if (hasText(t)) return t;
        }

        return null;
    }

    private static Object invokeIfExists(Object target, String method) {
        try {
            return target.getClass().getMethod(method).invoke(target);
        } catch (Exception e) {
            return null;
        }
    }

    private static Object unwrapJsonField(Object v) {
        if (v == null) return null;

        Object nullable = invokeIfExists(v, "getNullable");
        if (nullable != null) return nullable;

        Object optional = invokeIfExists(v, "getOptional");
        if (optional instanceof Optional<?> o) return o.orElse(null);

        Object asOptional = invokeIfExists(v, "asOptional");
        if (asOptional instanceof Optional<?> o) return o.orElse(null);

        return v;
    }

    /* ======================================================
       misc utils
       ====================================================== */

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String safe(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "";
    }

    private static String safeHead(String s) {
        if (s == null) return "";
        return s.length() <= 10 ? s : s.substring(0, 10);
    }

    private static boolean isTimeoutLike(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SocketTimeoutException ||
                    t instanceof HttpTimeoutException ||
                    t instanceof java.util.concurrent.TimeoutException) {
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
}
