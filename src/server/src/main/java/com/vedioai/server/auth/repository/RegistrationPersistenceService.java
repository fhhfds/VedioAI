package com.vedioai.server.auth.repository;

import com.vedioai.server.auth.challenge.ChallengeCrypto;
import com.vedioai.server.auth.challenge.ChallengeVerification;
import com.vedioai.server.auth.dto.AuthUserView;
import com.vedioai.server.auth.exception.ChallengeInvalidException;
import com.vedioai.server.auth.exception.EmailAlreadyRegisteredException;
import com.vedioai.server.common.web.RequestMetadata;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class RegistrationPersistenceService {

    private static final String PURPOSE_REGISTER = "REGISTER";
    private static final List<String> USER_PERMISSIONS = List.of(
            "account:self:read",
            "account:self:write",
            "session:self:manage",
            "media:own:read",
            "media:own:write",
            "media:own:play",
            "media:own:delete",
            "job:own:read");

    private final JdbcTemplate jdbcTemplate;
    private final ChallengeCrypto challengeCrypto;
    private final SecureRandom secureRandom = new SecureRandom();

    public RegistrationPersistenceService(
            JdbcTemplate jdbcTemplate,
            ChallengeCrypto challengeCrypto) {

        this.jdbcTemplate = jdbcTemplate;
        this.challengeCrypto = challengeCrypto;
    }

    @Transactional
    public AuthUserView create(
            ChallengeVerification verification,
            String code,
            String browserBinding,
            String nickname,
            String passwordHash,
            String userUid,
            RequestMetadata metadata) {

        lockAndVerify(verification, code, browserBinding);

        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_user WHERE email_norm = ?",
                Integer.class,
                verification.emailNorm());
        if (existing != null && existing > 0) {
            throw new EmailAlreadyRegisteredException();
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

        Long roleId;
        try {
            roleId = jdbcTemplate.queryForObject(
                    "SELECT id FROM auth_role WHERE role_code = 'USER'",
                    Long.class);
        } catch (EmptyResultDataAccessException exception) {
            throw new IllegalStateException("USER role is missing", exception);
        }
        if (roleId == null) {
            throw new IllegalStateException("USER role is missing");
        }

        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(
                        """
                        INSERT INTO auth_user
                            (user_uid, email_norm, email_display, nickname, status, email_verified_at)
                        VALUES
                            (?, ?, ?, ?, 'ACTIVE', UTC_TIMESTAMP(3))
                        """,
                        Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, userUid);
                statement.setString(2, verification.emailNorm());
                statement.setString(3, verification.emailNorm());
                statement.setString(4, nickname);
                return statement;
            }, keyHolder);
        } catch (DuplicateKeyException exception) {
            throw new EmailAlreadyRegisteredException();
        }

        Number generatedKey = keyHolder.getKey();
        if (generatedKey == null) {
            throw new IllegalStateException("user id was not generated");
        }
        long userId = generatedKey.longValue();

        jdbcTemplate.update(
                """
                INSERT INTO auth_password_credential
                    (user_id, password_hash, credential_version, password_changed_at)
                VALUES (?, ?, 1, UTC_TIMESTAMP(3))
                """,
                userId,
                passwordHash);

        jdbcTemplate.update(
                """
                INSERT INTO auth_user_role
                    (user_id, role_id, assigned_by_user_id)
                VALUES (?, ?, NULL)
                """,
                userId,
                roleId);

        jdbcTemplate.update(
                """
                INSERT INTO auth_audit_event
                    (event_uid, actor_user_id, subject_user_id, session_uid, event_type,
                     outcome, reason_code, identifier_hmac, ip_prefix, user_agent_label,
                     trace_id, context_json)
                VALUES
                    (?, NULL, ?, NULL, 'REGISTER', 'SUCCESS', NULL, NULL, ?, ?, ?, NULL)
                """,
                "evt_" + randomToken(18),
                userId,
                safe(metadata.ipPrefix(), "unknown", 64),
                safe(metadata.userAgentLabel(), "unknown", 160),
                safe(metadata.traceId(), "unknown", 128));

        return new AuthUserView(
                userUid,
                verification.emailNorm(),
                nickname,
                "ACTIVE",
                true,
                List.of("USER"),
                USER_PERMISSIONS,
                1,
                Instant.now().toString());
    }

    private void lockAndVerify(
            ChallengeVerification verification,
            String code,
            String browserBinding) {

        Long channelGeneration;
        try {
            channelGeneration = jdbcTemplate.queryForObject(
                    "SELECT generation FROM auth_challenge_channel WHERE id = ? FOR UPDATE",
                    Long.class,
                    verification.channelId());
        } catch (EmptyResultDataAccessException exception) {
            throw new ChallengeInvalidException();
        }
        if (channelGeneration == null || channelGeneration != verification.generation()) {
            throw new ChallengeInvalidException();
        }

        Map<String, Object> row;
        try {
            row = jdbcTemplate.queryForMap(
                    """
                    SELECT status, version, generation, email_norm, otp_mac,
                           browser_binding_mac,
                           (expires_at > UTC_TIMESTAMP(3)) AS not_expired
                    FROM auth_verification_challenge
                    WHERE id = ?
                    FOR UPDATE
                    """,
                    verification.challengeId());
        } catch (EmptyResultDataAccessException exception) {
            throw new ChallengeInvalidException();
        }

        String status = (String) row.get("status");
        long version = ((Number) row.get("version")).longValue();
        long generation = ((Number) row.get("generation")).longValue();
        boolean notExpired = ((Number) row.get("not_expired")).intValue() == 1;

        if (!"PENDING".equals(status)
                || version != verification.version()
                || generation != verification.generation()
                || !notExpired) {
            throw new ChallengeInvalidException();
        }

        byte[] expectedBinding = challengeCrypto.hmacBinding(browserBinding);
        if (!MessageDigest.isEqual(expectedBinding, (byte[]) row.get("browser_binding_mac"))) {
            throw new ChallengeInvalidException();
        }

        byte[] expectedMac = challengeCrypto.hmacChallenge(
                PURPOSE_REGISTER,
                verification.challengeUid(),
                verification.emailNorm(),
                code);
        if (!MessageDigest.isEqual(expectedMac, (byte[]) row.get("otp_mac"))) {
            throw new ChallengeInvalidException();
        }
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
