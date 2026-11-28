package com.vedioai.server.auth.controller;

import com.vedioai.server.auth.domain.AuthenticatedUser;
import com.vedioai.server.auth.dto.AuthStateResponse;
import com.vedioai.server.auth.exception.AlreadyAuthenticatedException;
import com.vedioai.server.auth.exception.SessionExpiredException;
import com.vedioai.server.auth.exception.SessionGuardException;
import com.vedioai.server.auth.security.SessionGuardService;
import com.vedioai.server.auth.service.AuthLoginService;
import com.vedioai.server.auth.service.MeService;
import com.vedioai.server.auth.service.SessionLoginService;
import com.vedioai.server.common.api.ApiResponse;
import com.vedioai.server.common.identity.CurrentActor;
import com.vedioai.server.common.identity.CurrentActorProvider;
import com.vedioai.server.common.web.RequestMetadata;
import com.vedioai.server.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/auth")
public class SessionController {

    private final AuthLoginService authLoginService;
    private final SessionLoginService sessionLoginService;
    private final MeService meService;
    private final SessionGuardService sessionGuardService;
    private final CurrentActorProvider currentActorProvider;
    private final SecurityContextRepository securityContextRepository;

    public SessionController(
            AuthLoginService authLoginService,
            SessionLoginService sessionLoginService,
            MeService meService,
            SessionGuardService sessionGuardService,
            CurrentActorProvider currentActorProvider,
            SecurityContextRepository securityContextRepository) {

        this.authLoginService = authLoginService;
        this.sessionLoginService = sessionLoginService;
        this.meService = meService;
        this.sessionGuardService = sessionGuardService;
        this.currentActorProvider = currentActorProvider;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/login")
    public ApiResponse<AuthStateResponse> login(
            @Valid @RequestBody LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {

        response.setHeader("Cache-Control", "no-store");

        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        if (existing != null && existing.isAuthenticated()
                && !(existing instanceof AnonymousAuthenticationToken)) {
            throw new AlreadyAuthenticatedException();
        }

        AuthenticatedUser user = authLoginService.authenticate(body.email(), body.password());

        request.getSession(true);
        request.changeSessionId();
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new SessionExpiredException();
        }
        String sessionId = session.getId();

        RequestMetadata metadata = new RequestMetadata(
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                TraceIdFilter.traceId(request));

        AuthStateResponse state = sessionLoginService.start(user, sessionId, metadata);

        CurrentActor actor = new CurrentActor(
                user.userId(),
                user.userUid(),
                Set.copyOf(user.roles()),
                state.session().sessionId(),
                user.authVersion());
        List<SimpleGrantedAuthority> authorities = user.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                actor, null, authorities);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        try {
            securityContextRepository.saveContext(context, request, response);
        } catch (RuntimeException exception) {
            sessionGuardService.revoke(user.userId(), sessionId, "SESSION_SAVE_FAILED");
            throw exception;
        }

        return new ApiResponse<>(state, TraceIdFilter.traceId(request));
    }

    @GetMapping("/me")
    public ApiResponse<AuthStateResponse> me(
            HttpServletRequest request,
            HttpServletResponse response) {

        response.setHeader("Cache-Control", "no-store");

        CurrentActor actor;
        try {
            actor = currentActorProvider.requireCurrentActor();
        } catch (InsufficientAuthenticationException exception) {
            throw new SessionExpiredException();
        }

        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new SessionExpiredException();
        }

        try {
            AuthStateResponse state = meService.me(actor.userId(), session.getId());
            return new ApiResponse<>(state, TraceIdFilter.traceId(request));
        } catch (SessionGuardException exception) {
            session.invalidate();
            SecurityContextHolder.clearContext();
            throw new SessionExpiredException();
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response) {

        response.setHeader("Cache-Control", "no-store");

        HttpSession session = request.getSession(false);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (session != null && authentication != null
                && authentication.getPrincipal() instanceof CurrentActor actor) {
            try {
                sessionGuardService.revoke(actor.userId(), session.getId(), "LOGOUT");
            } catch (RuntimeException ignored) {
                // Logout still clears the framework session.
            }
        }

        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        return ResponseEntity.noContent().build();
    }

    public record LoginRequest(
            @NotBlank
            @Size(max = 254)
            String email,
            @NotBlank
            @Size(max = 1024)
            String password) {
    }
}
