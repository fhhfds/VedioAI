package com.vedioai.server.auth.controller;

import com.vedioai.server.auth.challenge.ChallengeReceipt;
import com.vedioai.server.auth.challenge.RegistrationChallengeService;
import com.vedioai.server.auth.config.VedioaiAuthProperties;
import com.vedioai.server.auth.dto.AuthUserView;
import com.vedioai.server.auth.exception.RegistrationDisabledException;
import com.vedioai.server.auth.security.BrowserBindings;
import com.vedioai.server.auth.service.RegistrationService;
import com.vedioai.server.common.api.ApiResponse;
import com.vedioai.server.common.web.RequestMetadata;
import com.vedioai.server.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/auth")
public class RegistrationController {

    private final VedioaiAuthProperties properties;
    private final RegistrationChallengeService challengeService;
    private final RegistrationService registrationService;

    public RegistrationController(
            VedioaiAuthProperties properties,
            RegistrationChallengeService challengeService,
            RegistrationService registrationService) {

        this.properties = properties;
        this.challengeService = challengeService;
        this.registrationService = registrationService;
    }

    @PostMapping("/registration-challenges")
    public ResponseEntity<ApiResponse<ChallengeReceipt>> requestChallenge(
            @Valid @RequestBody ChallengeRequest body,
            HttpSession session,
            HttpServletRequest request,
            HttpServletResponse response) {

        requireRegistrationEnabled();
        response.setHeader("Cache-Control", "no-store");

        ChallengeReceipt receipt = challengeService.request(
                body.email(),
                BrowserBindings.getOrCreate(session));

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new ApiResponse<>(receipt, TraceIdFilter.traceId(request)));
    }

    @PostMapping("/registrations")
    public ResponseEntity<ApiResponse<AuthUserView>> register(
            @Valid @RequestBody RegisterRequest body,
            HttpSession session,
            HttpServletRequest request,
            HttpServletResponse response) {

        requireRegistrationEnabled();
        response.setHeader("Cache-Control", "no-store");

        AuthUserView user = registrationService.register(
                body.challengeId(),
                body.code(),
                body.nickname(),
                body.password(),
                BrowserBindings.getOrCreate(session),
                new RequestMetadata(
                        request.getRemoteAddr(),
                        request.getHeader("User-Agent"),
                        TraceIdFilter.traceId(request)));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(user, TraceIdFilter.traceId(request)));
    }

    private void requireRegistrationEnabled() {
        if (!properties.isRegistrationEnabled()) {
            throw new RegistrationDisabledException();
        }
    }

    public record ChallengeRequest(
            @NotBlank
            @Size(max = 254)
            String email) {
    }

    public record RegisterRequest(
            @NotBlank
            @Size(max = 128)
            String challengeId,
            @NotBlank
            @Pattern(regexp = "\\d{6}", message = "验证码必须为 6 位数字")
            String code,
            @NotBlank
            @Size(max = 64)
            String nickname,
            @NotBlank
            @Size(max = 128)
            String password) {
    }
}
