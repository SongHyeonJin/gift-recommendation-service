package com.example.giftrecommender.common.logging.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class ResponseLoggingInterceptor implements HandlerInterceptor {

    private static final String TRACE_ID = "traceId";

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) throws Exception {
        String traceId = MDC.get(TRACE_ID);

        if (response instanceof ContentCachingResponseWrapper wrapper) {
            String responseBody = new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
            log.info("Response [{}] Status: {}, Body: {}", traceId, response.getStatus(), responseBody);
        } else {
            log.info("Response [{}] Status: {}", traceId, response.getStatus());
        }

        MDC.clear();
    }
}
