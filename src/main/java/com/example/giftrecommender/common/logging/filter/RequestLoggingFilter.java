package com.example.giftrecommender.common.logging.filter;

import com.example.giftrecommender.common.logging.LogEventService;
import com.example.giftrecommender.common.logging.LoggingExcludes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String TRACE_ID = "traceId";
    private final LogEventService logEventService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        if (!uri.startsWith(LoggingExcludes.API_PREFIX)) return true;

        for (String p : LoggingExcludes.URL_PREFIXES) {
            if (uri.startsWith(p)) return true;
        }
        return "OPTIONS".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        String traceId = UUID.randomUUID().toString();
        MDC.put(TRACE_ID, traceId);
        wrappedRequest.setAttribute(TRACE_ID, traceId);

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);


            String contentType = wrappedRequest.getContentType();
            byte[] cached = wrappedRequest.getContentAsByteArray();

            if (cached.length > 0 && contentType != null && contentType.contains("application/json")) {
                String body = new String(cached, StandardCharsets.UTF_8);
                log.info("Request [{}] {} {} JSON body: {}", traceId,
                        wrappedRequest.getMethod(), wrappedRequest.getRequestURI(), body);

                String logMessage = String.format("Request %s %s\nBody: %s",
                        wrappedRequest.getMethod(), wrappedRequest.getRequestURI(), body);
                logEventService.log("INFO", "RequestLoggingFilter", logMessage);
            }
        } finally {
            wrappedResponse.copyBodyToResponse();
        }
    }
}
