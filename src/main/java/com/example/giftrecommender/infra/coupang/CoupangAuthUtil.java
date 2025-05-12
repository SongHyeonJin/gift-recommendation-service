package com.example.giftrecommender.infra.coupang;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * 쿠팡 OpenAPI 인증 유틸리티 클래스
 * - RFC2104 HMAC-SHA256 기반 서명 생성
 * - 쿠팡 API 호출 시 Authorization 헤더 생성에 사용
 */
@Slf4j
public class CoupangAuthUtil {

    private static final String ALGORITHM = "HmacSHA256";

    /**
     * HMAC 서명 기반 Authorization 문자열 생성
     *
     * @param method     HTTP 메서드 (예: "GET")
     * @param path       요청 경로 (예: "/v2/providers/affiliate_open_api/apis/openapi/v1/products/search")
     * @param rawQuery   쿼리 파라미터 문자열 (예: "keyword=인코딩된 키워드")
     * @param secretKey  쿠팡 제공 Secret Key
     * @param accessKey  쿠팡 제공 Access Key
     * @param timestamp  요청 시점의 타임스탬프 ("yyMMdd'T'HHmmss'Z'" 형식)
     * @return Authorization 헤더 값
     *
     * message 형식: {timestamp}{method}{path}{query}
     */
    public static String generateAuthorization(String method, String path, String rawQuery,
                                               String secretKey, String accessKey, String timestamp) {
        try {
            // 서명 대상 문자열 조합 (중요: ? 없이 path+query 연결)
            String message = timestamp + method + path + rawQuery;
            log.info("🧾 HMAC message = {}", message);

            // HMAC-SHA256 알고리즘 기반 서명 생성
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            String signature = Hex.encodeHexString(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));

            // 최종 Authorization 포맷 구성
            return String.format("CEA algorithm=%s, access-key=%s, signed-date=%s, signature=%s",
                    ALGORITHM, accessKey, timestamp, signature);
        } catch (Exception e) {
            throw new RuntimeException("HMAC 생성 실패", e);
        }
    }

    /**
     * 쿠팡 HMAC 요청용 timestamp 생성 (ex. 240512T081200Z)
     */
    public static String generateTimestamp() {
        SimpleDateFormat dateFormatGmt = new SimpleDateFormat("yyMMdd'T'HHmmss'Z'");
        dateFormatGmt.setTimeZone(TimeZone.getTimeZone("GMT"));
        return dateFormatGmt.format(new Date());
    }

}
