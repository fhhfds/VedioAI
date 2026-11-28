package com.vedioai.server.auth.controller;

import com.vedioai.server.auth.challenge.ChallengeReceipt;
import com.vedioai.server.auth.challenge.PasswordResetChallengeService;
import com.vedioai.server.auth.security.BrowserBindings;
import com.vedioai.server.auth.service.PasswordResetService;
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
public class PasswordResetController {

    private final PasswordResetChallengeService challengeService;
    private final PasswordResetService passwordResetService;

    public PasswordResetController(
            PasswordResetChallengeService challengeService,
            PasswordResetService passwordResetService) {

        this.challengeService = challengeService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/password-reset-challenges")
    public ResponseEntity<ApiResponse<ChallengeReceipt>> requestChallenge(
            @Valid @RequestBody ChallengeRequest body,
            HttpSession session,
            HttpServletRequest request,
            HttpServletResponse response) {

        response.setHeader("Cache-Control", "no-store");
        ChallengeReceipt receipt = challengeService.request(
                body.email(),
                BrowserBindings.getOrCreate(session));

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new ApiResponse<>(receipt, TraceIdFilter.traceId(request)));
    }

    @PostMapping("/password/resets")
    public ResponseEntity<Void> reset(
            @Valid @RequestBody PasswordResetRequest body,
            HttpSession session,
            HttpServletRequest request,
            HttpServletResponse response) {

        response.setHeader("Cache-Control", "no-store");
        passwordResetService.reset(
                body.challengeId(),
                body.code(),
                body.newPassword(),
                BrowserBindings.getOrCreate(session),
                new RequestMetadata(
                        request.getRemoteAddr(),
                        request.getHeader("User-Agent"),
                        TraceIdFilter.traceId(request)));

        return ResponseEntity.noContent().build();
    }

    public record ChallengeRequest(
            @NotBlank
            @Size(max = 254)
            String email) {
    }

    public record PasswordResetRequest(
            @NotBlank
            @Size(max = 128)
            String challengeId,
            @NotBlank
            @Pattern(regexp = "\\d{6}", message = "验证码必须为 6 位数字")
            String code,
            @NotBlank
            @Size(max = 128)
            String newPassword) {
    }
}
