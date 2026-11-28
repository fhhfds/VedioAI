package com.vedioai.server.auth.controller;

import com.vedioai.server.auth.config.VedioaiAuthProperties;
import com.vedioai.server.auth.security.PasswordPolicy;
import com.vedioai.server.common.api.ApiResponse;
import com.vedioai.server.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/auth")
public class AuthBoundaryController {

    private static final long IDLE_TIMEOUT_SECONDS = 1800;

    private final VedioaiAuthProperties properties;

    public AuthBoundaryController(VedioaiAuthProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/policy")
    public ApiResponse<PolicyResponse> policy(HttpServletRequest request) {
        VedioaiAuthProperties.Challenge challenge = properties.getChallenge();
        return new ApiResponse<>(
                new PolicyResponse(
                        properties.isRegistrationEnabled(),
                        PasswordPolicy.MIN_CODE_POINTS,
                        PasswordPolicy.MAX_CODE_POINTS,
                        challenge.getOtpLength(),
                        challenge.getTtl().toSeconds(),
                        challenge.getResendAfter().toSeconds(),
                        IDLE_TIMEOUT_SECONDS,
                        properties.getSession().getAbsoluteTtl().toSeconds(),
                        properties.getSession().getMaxActive()),
                TraceIdFilter.traceId(request));
    }

    @GetMapping("/csrf")
    public ApiResponse<CsrfResponse> csrf(CsrfToken token, HttpServletRequest request) {
        return new ApiResponse<>(
                new CsrfResponse(token.getHeaderName(), token.getToken()),
                TraceIdFilter.traceId(request));
    }

    public record PolicyResponse(
            boolean registrationEnabled,
            int passwordMinCodePoints,
            int passwordMaxCodePoints,
            int otpLength,
            long otpTtlSeconds,
            long resendAfterSeconds,
            long idleTimeoutSeconds,
            long absoluteTimeoutSeconds,
            int maxSessions) {
    }

    public record CsrfResponse(String headerName, String token) {
    }
}
