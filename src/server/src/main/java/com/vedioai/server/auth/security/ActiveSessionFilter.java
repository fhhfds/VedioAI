package com.vedioai.server.auth.security;

import com.vedioai.server.common.api.ApiErrorWriter;
import com.vedioai.server.common.identity.CurrentActor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;


/**
 * Re-checks the guard ledger, user status and auth version for every authenticated
 * request. Login creates a PENDING guard; /api/auth/me activates it, so /me is
 * intentionally excluded here and performs the same checks plus activation.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class ActiveSessionFilter extends OncePerRequestFilter {

    private static final Set<String> EXCLUDED_PATHS = Set.of(
            "/api/health",
            "/actuator/health",
            "/api/auth/policy",
            "/api/auth/csrf",
            "/api/auth/me",
            "/api/auth/logout",
            "/error");

    private final SessionGuardService sessionGuardService;

    public ActiveSessionFilter(SessionGuardService sessionGuardService) {
        this.sessionGuardService = sessionGuardService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return EXCLUDED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CurrentActor actor)) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            reject(request, response, null);
            return;
        }

        try {
            sessionGuardService.requireActive(actor.userId(), actor.sessionUid(), session.getId());
        } catch (RuntimeException exception) {
            SecurityContextHolder.clearContext();
            reject(request, response, session);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void reject(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpSession session) throws IOException {
        if (session != null) {
            try {
                session.invalidate();
            } catch (IllegalStateException ignored) {
                // Already invalidated by another request.
            }
        }
        ApiErrorWriter.write(
                request,
                response,
                HttpStatus.UNAUTHORIZED.value(),
                "SESSION_EXPIRED",
                "会话已失效，请重新登录");
    }
}
