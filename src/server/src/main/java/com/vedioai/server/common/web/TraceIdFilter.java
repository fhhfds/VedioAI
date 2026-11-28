package com.vedioai.server.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;


@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-Id";

    private static final String ATTRIBUTE = TraceIdFilter.class.getName() + ".traceId";
    private static final Pattern VALID_TRACE_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String candidate = request.getHeader(HEADER_NAME);
        String traceId = candidate != null && VALID_TRACE_ID.matcher(candidate).matches()
                ? candidate
                : UUID.randomUUID().toString();

        request.setAttribute(ATTRIBUTE, traceId);
        response.setHeader(HEADER_NAME, traceId);

        filterChain.doFilter(request, response);
    }

    public static String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(ATTRIBUTE);
        return value instanceof String traceId && VALID_TRACE_ID.matcher(traceId).matches()
                ? traceId
                : "unknown";
    }
}
