package com.example.giftrecommender.common.logging.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String TRACE_ID = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        String traceId = UUID.randomUUID().toString();
        MDC.put(TRACE_ID, traceId);

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
            logRequest(wrappedRequest, traceId);
        } finally {
            wrappedResponse.copyBodyToResponse();
            MDC.clear();
        }
    }

    private void logRequest(ContentCachingRequestWrapper request, String traceId) {
        MDC.put(TRACE_ID, traceId);

        String contentType = request.getContentType();

        if (request.getContentLength() == 0) {
            log.info("Request [{}] body is empty", traceId);
            return;
        }

        if (contentType != null && contentType.contains("application/json")) {
            String body = new String(request.getContentAsByteArray(), StandardCharsets.UTF_8);
            log.info("Request [{}] JSON body: {}", traceId, body);
        } else {
            log.info("Request [{}] Unsupported content-type: {}", traceId, contentType);
        }

    }
}
