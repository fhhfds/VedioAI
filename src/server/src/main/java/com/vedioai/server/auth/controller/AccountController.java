package com.vedioai.server.auth.controller;

import com.vedioai.server.auth.dto.AuthUserView;
import com.vedioai.server.auth.dto.ReauthResultView;
import com.vedioai.server.auth.dto.SessionListView;
import com.vedioai.server.auth.exception.SessionExpiredException;
import com.vedioai.server.auth.service.AccountSessionService;
import com.vedioai.server.auth.service.PasswordManagementService;
import com.vedioai.server.auth.service.ProfileService;
import com.vedioai.server.auth.service.ReauthenticationService;
import com.vedioai.server.auth.service.SessionsService;
import com.vedioai.server.common.api.ApiResponse;
import com.vedioai.server.common.identity.CurrentActor;
import com.vedioai.server.common.identity.CurrentActorProvider;
import com.vedioai.server.common.web.RequestMetadata;
import com.vedioai.server.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/auth")
public class AccountController {

    private final CurrentActorProvider currentActorProvider;
    private final ProfileService profileService;
    private final ReauthenticationService reauthenticationService;
    private final SessionsService sessionsService;
    private final AccountSessionService accountSessionService;
    private final PasswordManagementService passwordManagementService;

    public AccountController(
            CurrentActorProvider currentActorProvider,
            ProfileService profileService,
            ReauthenticationService reauthenticationService,
            SessionsService sessionsService,
            AccountSessionService accountSessionService,
            PasswordManagementService passwordManagementService) {

        this.currentActorProvider = currentActorProvider;
        this.profileService = profileService;
        this.reauthenticationService = reauthenticationService;
        this.sessionsService = sessionsService;
        this.accountSessionService = accountSessionService;
        this.passwordManagementService = passwordManagementService;
    }

    @PatchMapping("/profile")
    public ApiResponse<AuthUserView> updateProfile(
            @Valid @RequestBody ProfileRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {

        noStore(response);
        CurrentActor actor = currentActorProvider.requireCurrentActor();
        AuthUserView user = profileService.updateNickname(
                actor.userId(),
                body.nickname(),
                body.expectedVersion(),
                metadata(request));
        return new ApiResponse<>(user, TraceIdFilter.traceId(request));
    }

    @PostMapping("/reauthentication")
    public ApiResponse<ReauthResultView> reauthenticate(
            @Valid @RequestBody ReauthRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {

        noStore(response);
        CurrentActor actor = currentActorProvider.requireCurrentActor();
        HttpSession session = requireSession(request);
        Instant validUntil = reauthenticationService.reauthenticate(
                actor.userId(),
                actor.sessionUid(),
                session.getId(),
                body.password());
        reauthenticationService.mark(session, validUntil);
        return new ApiResponse<>(
                new ReauthResultView(validUntil.toString()),
                TraceIdFilter.traceId(request));
    }

    @GetMapping("/sessions")
    public ApiResponse<SessionListView> sessions(
            HttpServletRequest request,
            HttpServletResponse response) {

        noStore(response);
        CurrentActor actor = currentActorProvider.requireCurrentActor();
        return new ApiResponse<>(
                new SessionListView(sessionsService.list(actor.userId(), actor.sessionUid())),
                TraceIdFilter.traceId(request));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(
            @PathVariable
            @NotBlank
            @Size(max = 128)
            String sessionId,
            HttpServletRequest request,
            HttpServletResponse response) {

        noStore(response);
        CurrentActor actor = currentActorProvider.requireCurrentActor();
        sessionsService.revoke(actor.userId(), sessionId);

        if (actor.sessionUid().equals(sessionId)) {
            invalidate(request);
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/revoke-all")
    public ResponseEntity<Void> revokeAll(
            HttpServletRequest request,
            HttpServletResponse response) {

        noStore(response);
        CurrentActor actor = currentActorProvider.requireCurrentActor();
        HttpSession session = requireSession(request);
        reauthenticationService.require(session);
        accountSessionService.revokeAll(
                actor.userId(),
                actor.sessionUid(),
                session.getId(),
                metadata(request));
        invalidate(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/change")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody PasswordChangeRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {

        noStore(response);
        CurrentActor actor = currentActorProvider.requireCurrentActor();
        HttpSession session = requireSession(request);
        passwordManagementService.change(
                actor.userId(),
                actor.sessionUid(),
                session.getId(),
                body.currentPassword(),
                body.newPassword(),
                metadata(request));
        invalidate(request);
        return ResponseEntity.noContent().build();
    }

    private HttpSession requireSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new com.vedioai.server.auth.exception.SessionExpiredException();
        }
        return session;
    }

    private void invalidate(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    private void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    private RequestMetadata metadata(HttpServletRequest request) {
        return new RequestMetadata(
                request.getRemoteAddr(),
                request.getHeader("User-Agent"),
                TraceIdFilter.traceId(request));
    }

    public record ProfileRequest(
            @NotBlank
            @Size(max = 64)
            String nickname,
            @NotNull
            @Min(1)
            @Max(Long.MAX_VALUE)
            Long expectedVersion) {
    }

    public record ReauthRequest(
            @NotBlank
            @Size(max = 1024)
            String password) {
    }

    public record PasswordChangeRequest(
            @NotBlank
            @Size(max = 1024)
            String currentPassword,
            @NotBlank
            @Size(max = 128)
            String newPassword) {
    }
}
