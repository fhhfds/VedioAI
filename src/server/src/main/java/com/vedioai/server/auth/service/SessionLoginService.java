package com.vedioai.server.auth.service;

import com.vedioai.server.auth.config.VedioaiAuthProperties;
import com.vedioai.server.auth.domain.AuthenticatedUser;
import com.vedioai.server.auth.dto.AuthStateResponse;
import com.vedioai.server.auth.dto.AuthUserViews;
import com.vedioai.server.auth.dto.SessionView;
import com.vedioai.server.auth.exception.InvalidCredentialsException;
import com.vedioai.server.auth.exception.SessionLimitException;
import com.vedioai.server.auth.security.SessionGuardService;
import com.vedioai.server.auth.security.SessionGuardView;
import com.vedioai.server.common.web.RequestMetadata;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class SessionLoginService {

    private static final long PENDING_TTL_SECONDS = 120;

    private final JdbcTemplate jdbcTemplate;
    private final SessionGuardService sessionGuardService;
    private final VedioaiAuthProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public SessionLoginService(
            JdbcTemplate jdbcTemplate,
            SessionGuardService sessionGuardService,
            VedioaiAuthProperties properties) {

        this.jdbcTemplate = jdbcTemplate;
        this.sessionGuardService = sessionGuardService;
        this.properties = properties;
    }

    @Transactional
    public AuthStateResponse start(
            AuthenticatedUser user,
            String frameworkSessionId,
            RequestMetadata metadata) {

        Map<String, Object> lockedUser;
        try {
            lockedUser = jdbcTemplate.queryForMap(
                    """
                    SELECT id, status, auth_version, version, user_uid, email_norm,
                           nickname, created_at, email_verified_at
                    FROM auth_user
                    WHERE id = ?
                    FOR UPDATE
                    """,
                    user.userId());
        } catch (EmptyResultDataAccessException exception) {
            throw new InvalidCredentialsException();
        }

        String status = (String) lockedUser.get("status");
        long authVersion = ((Number) lockedUser.get("auth_version")).longValue();
        if (!"ACTIVE".equals(status) || authVersion != user.authVersion()) {
            throw new InvalidCredentialsException();
        }

        Map<String, Object> credential;
        try {
            credential = jdbcTemplate.queryForMap(
                    """
                    SELECT password_hash, credential_version
                    FROM auth_password_credential
                    WHERE user_id = ?
                    FOR UPDATE
                    """,
                    user.userId());
        } catch (EmptyResultDataAccessException exception) {
            throw new InvalidCredentialsException();
        }

        String currentHash = (String) credential.get("password_hash");
        long credentialVersion = ((Number) credential.get("credential_version")).longValue();
        if (!currentHash.equals(user.passwordHash()) || credentialVersion != user.credentialVersion()) {
            throw new InvalidCredentialsException();
        }

        jdbcTemplate.update(
                """
                UPDATE auth_session_guard
                SET status = 'EXPIRED', updated_at = UTC_TIMESTAMP(3)
                WHERE user_id = ?
                  AND status IN ('PENDING', 'ACTIVE')
                  AND (absolute_expires_at <= UTC_TIMESTAMP(3)
                       OR (status = 'PENDING' AND pending_expires_at <= UTC_TIMESTAMP(3)))
                """,
                user.userId());

        Integer activeSessions = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM auth_session_guard
                WHERE user_id = ?
                  AND status IN ('PENDING', 'ACTIVE')
                  AND absolute_expires_at > UTC_TIMESTAMP(3)
                """,
                Integer.class,
                user.userId());

        int maxSessions = properties.getSession().getMaxActive();
        if (activeSessions != null && activeSessions >= maxSessions) {
            throw new SessionLimitException();
        }

        String sessionUid = "ses_" + randomToken(24);
        SessionGuardView guard = sessionGuardService.createPending(
                user.userId(),
                sessionUid,
                frameworkSessionId,
                authVersion,
                PENDING_TTL_SECONDS,
                properties.getSession().getAbsoluteTtl().toSeconds(),
                metadata == null ? null : metadata.userAgentLabel(),
                metadata == null ? null : metadata.ipPrefix());

        jdbcTemplate.update(
                "UPDATE auth_user SET last_login_at = UTC_TIMESTAMP(3) WHERE id = ?",
                user.userId());

        jdbcTemplate.update(
                """
                INSERT INTO auth_audit_event
                    (event_uid, actor_user_id, subject_user_id, session_uid, event_type,
                     outcome, reason_code, identifier_hmac, ip_prefix, user_agent_label,
                     trace_id, context_json)
                VALUES
                    (?, ?, ?, ?, 'LOGIN', 'SUCCESS', NULL, NULL, ?, ?, ?, NULL)
                """,
                "evt_" + randomToken(18),
                user.userId(),
                user.userId(),
                sessionUid,
                safe(metadata == null ? null : metadata.ipPrefix(), "unknown", 64),
                safe(metadata == null ? null : metadata.userAgentLabel(), "unknown", 160),
                safe(metadata == null ? null : metadata.traceId(), "unknown", 128));

        return new AuthStateResponse(
                AuthUserViews.of(
                        user.userUid(),
                        user.email(),
                        user.nickname(),
                        user.status(),
                        user.emailVerified(),
                        user.roles(),
                        user.version(),
                        user.createdAt()),
                new SessionView(
                        guard.sessionUid(),
                        guard.authenticatedAt(),
                        guard.absoluteExpiresAt()));
    }

    private String randomToken(int byteCount) {
        byte[] bytes = new byte[byteCount];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String safe(String value, String fallback, int maxLength) {
        String result = value == null || value.isBlank() ? fallback : value;
        return result.length() <= maxLength ? result : result.substring(0, maxLength);
    }
}
