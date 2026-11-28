package com.vedioai.server.auth.repository;

import com.vedioai.server.auth.challenge.ChallengeCrypto;
import com.vedioai.server.auth.challenge.ChallengeVerification;
import com.vedioai.server.auth.exception.AccountDisabledException;
import com.vedioai.server.auth.exception.ChallengeInvalidException;
import com.vedioai.server.auth.exception.CurrentPasswordInvalidException;
import com.vedioai.server.auth.exception.SessionExpiredException;
import com.vedioai.server.auth.exception.VersionConflictException;
import com.vedioai.server.auth.security.SessionGuardService;
import com.vedioai.server.auth.util.IdentityIds;
import com.vedioai.server.common.web.RequestMetadata;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class PasswordPersistenceService {

    private static final String PURPOSE_RESET = "RESET";

    private final JdbcTemplate jdbcTemplate;
    private final ChallengeCrypto challengeCrypto;
    private final SessionGuardService sessionGuardService;

    public PasswordPersistenceService(
            JdbcTemplate jdbcTemplate,
            ChallengeCrypto challengeCrypto,
            SessionGuardService sessionGuardService) {

        this.jdbcTemplate = jdbcTemplate;
        this.challengeCrypto = challengeCrypto;
        this.sessionGuardService = sessionGuardService;
    }

    @Transactional
    public void change(
            long userId,
            String sessionUid,
            String frameworkSessionId,
            String expectedHash,
            String newHash,
            RequestMetadata metadata) {

        Map<String, Object> user = lockUser(userId);
        requireActive(user);
        Map<String, Object> credential = lockCredential(userId);
        if (!expectedHash.equals(credential.get("password_hash"))) {
            throw new VersionConflictException();
        }

        sessionGuardService.requireActive(userId, sessionUid, frameworkSessionId);

        updateCredential(userId, newHash);
        jdbcTemplate.update(
                """
                UPDATE auth_user
                SET auth_version = auth_version + 1,
                    version = version + 1,
                    updated_at = UTC_TIMESTAMP(3)
                WHERE id = ?
                """,
                userId);

        sessionGuardService.revokeAll(userId, "PASSWORD");
        audit(userId, userId, "PASSWORD_CHANGE", metadata);
    }

    @Transactional
    public void reset(
            ChallengeVerification verification,
            String code,
            String browserBinding,
            String newHash,
            RequestMetadata metadata) {

        Long channelGeneration = lockChannel(verification.channelId());
        if (channelGeneration == null || channelGeneration != verification.generation()) {
            throw new ChallengeInvalidException();
        }

        Map<String, Object> challenge = lockChallenge(verification.challengeId());
        requireChallengeMatches(challenge, verification, code, browserBinding);

        Long userId = verification.subjectUserId();
        Long issuedAuthVersion = verification.issuedAuthVersion();
        if (userId == null || issuedAuthVersion == null) {
            throw new ChallengeInvalidException();
        }

        Map<String, Object> user = lockUser(userId);
        if (!"ACTIVE".equals(user.get("status"))
                || ((Number) user.get("auth_version")).longValue() != issuedAuthVersion) {
            throw new ChallengeInvalidException();
        }

        int consumed = jdbcTemplate.update(
                """
                UPDATE auth_verification_challenge
                SET status = 'USED',
                    used_at = UTC_TIMESTAMP(3),
                    version = version + 1,
                    updated_at = UTC_TIMESTAMP(3)
                WHERE id = ? AND status = 'PENDING' AND version = ?
                """,
                verification.challengeId(),
                verification.version());
        if (consumed != 1) {
            throw new ChallengeInvalidException();
        }

        updateCredential(userId, newHash);
        jdbcTemplate.update(
                """
                UPDATE auth_user
                SET auth_version = auth_version + 1,
                    version = version + 1,
                    updated_at = UTC_TIMESTAMP(3)
                WHERE id = ?
                """,
                userId);

        sessionGuardService.revokeAll(userId, "PASSWORD_RESET");
        audit(userId, userId, "PASSWORD_RESET", metadata);
    }

    private Map<String, Object> lockUser(long userId) {
        try {
            return jdbcTemplate.queryForMap(
                    """
                    SELECT id, status, auth_version, version
                    FROM auth_user
                    WHERE id = ?
                    FOR UPDATE
                    """,
                    userId);
        } catch (EmptyResultDataAccessException exception) {
            throw new SessionExpiredException();
        }
    }

    private Map<String, Object> lockCredential(long userId) {
        try {
            return jdbcTemplate.queryForMap(
                    """
                    SELECT password_hash, credential_version
                    FROM auth_password_credential
                    WHERE user_id = ?
                    FOR UPDATE
                    """,
                    userId);
        } catch (EmptyResultDataAccessException exception) {
            throw new CurrentPasswordInvalidException();
        }
    }

    private Long lockChannel(long channelId) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT generation FROM auth_challenge_channel WHERE id = ? FOR UPDATE",
                    Long.class,
                    channelId);
        } catch (EmptyResultDataAccessException exception) {
            return null;
        }
    }

    private Map<String, Object> lockChallenge(long challengeId) {
        try {
            return jdbcTemplate.queryForMap(
                    """
                    SELECT status, version, generation, email_norm, otp_mac,
                           browser_binding_mac, purpose,
                           (expires_at > UTC_TIMESTAMP(3)) AS not_expired
                    FROM auth_verification_challenge
                    WHERE id = ?
                    FOR UPDATE
                    """,
                    challengeId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ChallengeInvalidException();
        }
    }

    private void requireChallengeMatches(
            Map<String, Object> challenge,
            ChallengeVerification verification,
            String code,
            String browserBinding) {

        boolean pending = "PENDING".equals(challenge.get("status"));
        boolean notExpired = ((Number) challenge.get("not_expired")).intValue() == 1;
        long version = ((Number) challenge.get("version")).longValue();
        long generation = ((Number) challenge.get("generation")).longValue();
        String purpose = (String) challenge.get("purpose");

        if (!pending
                || !notExpired
                || version != verification.version()
                || generation != verification.generation()
                || !PURPOSE_RESET.equals(purpose)) {
            throw new ChallengeInvalidException();
        }

        byte[] expectedBinding = challengeCrypto.hmacBinding(browserBinding);
        if (!MessageDigest.isEqual(expectedBinding, (byte[]) challenge.get("browser_binding_mac"))) {
            throw new ChallengeInvalidException();
        }

        byte[] expectedMac = challengeCrypto.hmacChallenge(
                PURPOSE_RESET,
                verification.challengeUid(),
                verification.emailNorm(),
                code);
        if (!MessageDigest.isEqual(expectedMac, (byte[]) challenge.get("otp_mac"))) {
            throw new ChallengeInvalidException();
        }
    }

    private void updateCredential(long userId, String newHash) {
        jdbcTemplate.update(
                """
                UPDATE auth_password_credential
                SET password_hash = ?,
                    credential_version = credential_version + 1,
                    password_changed_at = UTC_TIMESTAMP(3),
                    updated_at = UTC_TIMESTAMP(3)
                WHERE user_id = ?
                """,
                newHash,
                userId);
    }

    private void requireActive(Map<String, Object> user) {
        if (!"ACTIVE".equals(user.get("status"))) {
            throw new AccountDisabledException();
        }
    }

    private void audit(
            long actorUserId,
            long subjectUserId,
            String eventType,
            RequestMetadata metadata) {

        jdbcTemplate.update(
                """
                INSERT INTO auth_audit_event
                    (event_uid, actor_user_id, subject_user_id, session_uid, event_type,
                     outcome, reason_code, identifier_hmac, ip_prefix, user_agent_label,
                     trace_id, context_json)
                VALUES
                    (?, ?, ?, NULL, ?, 'SUCCESS', NULL, NULL, ?, ?, ?, NULL)
                """,
                IdentityIds.eventUid(),
                actorUserId,
                subjectUserId,
                eventType,
                safe(metadata == null ? null : metadata.ipPrefix(), "unknown", 64),
                safe(metadata == null ? null : metadata.userAgentLabel(), "unknown", 160),
                safe(metadata == null ? null : metadata.traceId(), "unknown", 128));
    }

    private static String safe(String value, String fallback, int maxLength) {
        String result = value == null || value.isBlank() ? fallback : value;
        return result.length() <= maxLength ? result : result.substring(0, maxLength);
    }
}
