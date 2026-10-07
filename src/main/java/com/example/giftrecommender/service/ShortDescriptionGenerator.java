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
import java.util.Optional;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "openai", name = "enabled", havingValue = "true")
public class ShortDescriptionGenerator {

    // 비용/속도 밸런스
    private static final ChatModel MODEL = ChatModel.GPT_4_1_MINI;

    private final OpenAIClient client;
    private final boolean enabled;

    public ShortDescriptionGenerator(
            OpenAIClient client,
            @Value("${openai.enabled:true}") boolean enabled
    ) {
        this.client = client;
        this.enabled = enabled;
    }

    public String generate(CrawlingProduct p) {
        String title = safe(p.getDisplayName(), p.getOriginalName());
        String category = safe(p.getCategory());
        String platform = safe(p.getPlatform());
        String seller = safe(p.getSellerName());

        String prompt = buildPrompt(title, category, platform, seller);

        if (!enabled) {
            return normalize(title + "에 어울리는 실용적인 아이템");
        }

        int maxRetries = 3;
        Duration backoff = Duration.ofMillis(500);

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.debug("[SHORTDESC][CALL] model={}, attempt={}, title='{}'",
                        MODEL, attempt, safeHead(title));

                ResponseCreateParams params = ResponseCreateParams.builder()
                        .model(MODEL)
                        .input(prompt)
                        .maxOutputTokens(80)
                        .temperature(0.4)
                        .build();

                Response response = client.responses().create(params);

                // ✅ 여기서 "실제 텍스트"만 추출 (객체 toString 금지)
                String extracted = extractOutputTextCompat(response);

                String normalized = normalize(extracted);
                if (normalized == null) {
                    log.warn("[SHORTDESC][EMPTY] title='{}', extracted='{}', rawResponse={}",
                            safeHead(title), extracted, response);
                    throw new IllegalStateException("모델 응답이 비어있음");
                }

                log.debug("[SHORTDESC][OK] result='{}'", normalized);
                return normalized;

            } catch (RateLimitException e) {
                log.warn("[SHORTDESC][RETRY] RateLimit attempt={}/{}, backoff={}ms, msg={}",
                        attempt, maxRetries, backoff.toMillis(), e.getMessage());

            } catch (Exception e) {
                log.warn("[SHORTDESC][RETRY] {} attempt={}/{}, backoff={}ms, msg={}",
                        isTimeoutLike(e) ? "TimeoutLike" : e.getClass().getSimpleName(),
                        attempt, maxRetries, backoff.toMillis(), e.getMessage(), e);
            }

            sleep(backoff);
            backoff = backoff.multipliedBy(2);
        }

        throw new IllegalStateException("shortDescription 생성 실패 (모든 재시도 실패)");
    }

    /* ======================================================
       ✅ Responses API 텍스트 추출 (현재 SDK 호환 + toString 금지)
       ====================================================== */

    private static String extractOutputTextCompat(Response response) {
        if (response == null) return null;

        // 1) response.output()
        Object output = invokeIfExists(response, "output");
        String t1 = extractFromOutputList(output);
        if (hasText(t1)) return t1;

        // 2) response._output() (JsonField<List<...>>)
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

    /**
     * 어떤 객체가 오든 "실제 텍스트"만 찾아서 반환.
     * ⚠️ 절대 object.toString()으로 fallback 하지 않음 (ResponseOutputMessage 저장 방지)
     */
    private static String extractAnyText(Object obj, int depth) {
        if (obj == null) return null;
        if (depth > 7) return null; // 무한 재귀 방지

        // Optional 언랩
        if (obj instanceof Optional<?> opt) {
            return extractAnyText(opt.orElse(null), depth + 1);
        }

        // JsonField 언랩 (있으면)
        Object jf = unwrapJsonField(obj);
        if (jf != obj) {
            return extractAnyText(jf, depth + 1);
        }

        // String이면 그대로
        if (obj instanceof String s) {
            return s;
        }

        // List면 순회해서 합치기
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

        // ✅ content() 우선 탐색 (ResponseOutputMessage 내부 content에 실제 텍스트가 있음)
        Object content = invokeIfExists(obj, "content");
        if (content != null) {
            String t = extractAnyText(content, depth + 1);
            if (hasText(t)) return t;
        }

        // ✅ 흔한 텍스트 필드들 (String/JsonField/Optional 모두 재귀 처리)
        String[] direct = {"text", "inputText", "value", "outputText"};
        for (String m : direct) {
            Object v = invokeIfExists(obj, m);
            String t = extractAnyText(v, depth + 1);
            if (hasText(t)) return t;
        }

        // ✅ underscore 버전(JsonField)도 시도
        String[] unders = {"_text", "_inputText", "_value", "_outputText", "_content"};
        for (String m : unders) {
            Object v = unwrapJsonField(invokeIfExists(obj, m));
            String t = extractAnyText(v, depth + 1);
            if (hasText(t)) return t;
        }

        // ✅ message()가 있으면 message 객체 내부로 더 들어가서 탐색
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

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private String buildPrompt(String title, String category, String platform, String seller) {
        StringBuilder sb = new StringBuilder();
        sb.append("너는 상품 메타데이터를 정리하는 백엔드 운영 도우미야.\n");
        sb.append("아래 상품 정보를 참고해서 shortDescription을 한 줄로 만들어줘.\n");
        sb.append("카테고리는 참고 정보일 뿐이며, 없거나 부정확해도 문제 없어.\n");
        sb.append("상품명과 일반적인 사용 맥락을 기반으로 '잘 어울리는 용도/상황'을 추론해서 작성해.\n\n");

        sb.append("[상품정보]\n");
        sb.append("- 상품명: ").append(title).append("\n");
        if (!category.isBlank()) sb.append("- 카테고리(참고): ").append(category).append("\n");
        if (!platform.isBlank()) sb.append("- 플랫폼: ").append(platform).append("\n");
        if (!seller.isBlank()) sb.append("- 판매자: ").append(seller).append("\n");

        sb.append("\n[출력 규칙]\n");
        sb.append("1) 한국어 한 문장\n");
        sb.append("2) 20~40자 권장 (최대 60자)\n");
        sb.append("3) 광고/가격/혜택/구매 유도 표현 금지\n");
        sb.append("4) 상품의 용도, 사용 상황, 어울리는 맥락 위주로 작성\n");
        sb.append("5) 정보가 부족하면 일반적인 사용 사례를 자연스럽게 추론\n");
        sb.append("6) 출력은 문장만, 따옴표/마크다운/이모지 없이\n");

        return sb.toString();
    }

    private String normalize(String s) {
        if (s == null) return null;

        String trimmed = s.trim();
        if (trimmed.isEmpty()) return null;

        // 한 줄 강제
        trimmed = trimmed.replace("\n", " ").replace("\r", " ").replaceAll("\\s+", " ").trim();

        // 따옴표 제거
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
                (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }

        // ⚠️ 혹시라도 객체 문자열이 들어오면 컷 (2차 방어)
        if (trimmed.startsWith("ResponseOutput") || trimmed.startsWith("ResponseOutputMessage")) {
            return null;
        }

        // 60자 정책
        if (trimmed.length() > 60) trimmed = trimmed.substring(0, 60).trim();
        if (trimmed.length() > 255) trimmed = trimmed.substring(0, 255);

        return trimmed;
    }

    private String safe(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "";
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

    private static String safeHead(String s) {
        if (s == null) return "";
        return s.length() <= 10 ? s : s.substring(0, 10);
    }
}
