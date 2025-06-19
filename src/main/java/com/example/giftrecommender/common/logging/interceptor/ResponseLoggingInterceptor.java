package com.example.giftrecommender.common.logging.interceptor;

import com.example.giftrecommender.common.logging.LogEventService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResponseLoggingInterceptor implements HandlerInterceptor {

    private static final String TRACE_ID = "traceId";
    private final LogEventService logEventService;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        String traceId = MDC.get(TRACE_ID);
        int status = response.getStatus();

        if (response instanceof ContentCachingResponseWrapper wrapper) {
            String responseBody = new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8);

            // 콘솔/파일 로그
            log.info("Response [{}] Status: {}, Body: {}", traceId, status, responseBody);

            // DB 저장용 로그
            String logMessage = String.format("Response %s %s\nStatus: %d\nBody: %s",
                    request.getMethod(), request.getRequestURI(), status, responseBody);

            logEventService.log("INFO", "ResponseLoggingInterceptor", logMessage);
        } else {
            log.info("Response [{}] Status: {}", traceId, status);
            logEventService.log("INFO", "ResponseLoggingInterceptor",
                    String.format("Response %s %s\nStatus: %d",
                            request.getMethod(), request.getRequestURI(), status));
        }

        MDC.clear();
    }
}