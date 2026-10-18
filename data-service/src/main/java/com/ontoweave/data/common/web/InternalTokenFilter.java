package com.ontoweave.data.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ontoweave.data.common.config.OntoWeaveProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class InternalTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";

    private final byte[] expectedToken;
    private final ObjectMapper objectMapper;

    public InternalTokenFilter(OntoWeaveProperties properties, ObjectMapper objectMapper) {
        this.expectedToken = properties.internalToken().getBytes(StandardCharsets.UTF_8);
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        byte[] suppliedBytes = supplied == null
                ? new byte[0]
                : supplied.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedToken, suppliedBytes)) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(
                    response.getWriter(),
                    new ApiErrorResponse(
                            "UNAUTHORIZED",
                            "内部服务令牌无效",
                            Map.of(),
                            MDC.get(RequestIdFilter.MDC_KEY)
                    )
            );
            return;
        }
        filterChain.doFilter(request, response);
    }
}
