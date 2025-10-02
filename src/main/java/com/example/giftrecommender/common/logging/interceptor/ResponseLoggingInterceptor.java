package com.example.giftrecommender.common.logging.interceptor;

import com.example.giftrecommender.common.logging.LogEventService;
import com.example.giftrecommender.common.logging.LoggingExcludes;
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

    private boolean excluded(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        if (!uri.startsWith(LoggingExcludes.API_PREFIX)) return true;

        for (String p : LoggingExcludes.URL_PREFIXES) {
            if (uri.startsWith(p)) return true;
        }

        return "OPTIONS".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            if (excluded(request)) {
                return;
            }

            String traceId = (String) request.getAttribute(TRACE_ID);
            if (traceId == null) traceId = MDC.get(TRACE_ID);
            if (traceId == null) traceId = "-";

            int status = response.getStatus();

            if (response instanceof ContentCachingResponseWrapper wrapper) {
                byte[] bytes = wrapper.getContentAsByteArray();
                String responseBody = (bytes != null && bytes.length > 0)
                        ? new String(bytes, StandardCharsets.UTF_8)
                        : "";

                log.info("Response [{}] {} {} Status: {}, Body: {}",
                        traceId, request.getMethod(), request.getRequestURI(), status, responseBody);

                String logMessage = String.format("Response %s %s\nStatus: %d\nBody: %s",
                        request.getMethod(), request.getRequestURI(), status, responseBody);
                logEventService.log("INFO", "ResponseLoggingInterceptor", logMessage);
            } else {
                log.info("Response [{}] {} {} Status: {}",
                        traceId, request.getMethod(), request.getRequestURI(), status);
                String logMessage = String.format("Response %s %s\nStatus: %d",
                        request.getMethod(), request.getRequestURI(), status);
                logEventService.log("INFO", "ResponseLoggingInterceptor", logMessage);
            }
        } finally {
            MDC.clear();
        }
    }
}