package com.ecom.payments.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Requires a valid "Authorization: Bearer <token>" header on every payment endpoint it is registered for.
 * The gateway webhook is excluded in WebConfig because it is authenticated by its signature instead.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    public static final String AUTH_USER_ATTRIBUTE = "authUser";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX) || header.length() == BEARER_PREFIX.length()) {
            throw new UnauthorizedException("Missing Authorization: Bearer <token> header");
        }

        AuthUserDto user = authService.validateToken(header.substring(BEARER_PREFIX.length()).trim());
        request.setAttribute(AUTH_USER_ATTRIBUTE, user);
        return true;
    }
}
