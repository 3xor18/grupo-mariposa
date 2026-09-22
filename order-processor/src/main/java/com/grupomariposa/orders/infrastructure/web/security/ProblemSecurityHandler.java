package com.grupomariposa.orders.infrastructure.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupomariposa.orders.infrastructure.web.ApiErrorCode;
import com.grupomariposa.orders.infrastructure.web.ProblemFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

public final class ProblemSecurityHandler implements AuthenticationEntryPoint,
        AccessDeniedHandler {

    private static final String BEARER = "Bearer";
    private static final String UNAUTHORIZED = "A valid bearer token is required";
    private static final String FORBIDDEN = "The token does not grant access to this resource";

    private final ProblemFactory problems;
    private final ObjectMapper objectMapper;

    public ProblemSecurityHandler(final ProblemFactory problems, final ObjectMapper objectMapper) {
        this.problems = Objects.requireNonNull(problems, "problems");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public void commence(final HttpServletRequest request, final HttpServletResponse response,
                         final AuthenticationException failure) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BEARER);
        write(response, problems.create(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED,
                UNAUTHORIZED, request.getRequestURI()));
    }

    @Override
    public void handle(final HttpServletRequest request, final HttpServletResponse response,
                       final AccessDeniedException failure) throws IOException {
        write(response, problems.create(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN, FORBIDDEN,
                request.getRequestURI()));
    }

    private void write(final HttpServletResponse response, final ProblemDetail problem)
            throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
