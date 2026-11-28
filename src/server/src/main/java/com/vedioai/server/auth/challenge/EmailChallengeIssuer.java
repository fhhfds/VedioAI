package com.vedioai.server.auth.challenge;

import com.vedioai.server.auth.config.VedioaiAuthProperties;
import com.vedioai.server.auth.exception.ChallengeResendTooSoonException;
import com.vedioai.server.auth.mail.MailPayloadCipher;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class EmailChallengeIssuer {

    private final JdbcTemplate jdbcTemplate;
    private final ChallengeCrypto challengeCrypto;
    private final MailPayloadCipher mailPayloadCipher;
    private final VedioaiAuthProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailChallengeIssuer(
            JdbcTemplate jdbcTemplate,
            ChallengeCrypto challengeCrypto,
            MailPayloadCipher mailPayloadCipher,
            VedioaiAuthProperties properties) {

        this.jdbcTemplate = jdbcTemplate;
        this.challengeCrypto = challengeCrypto;
        this.mailPayloadCipher = mailPayloadCipher;
        this.properties = properties;
    }

    @Transactional
    public ChallengeReceipt issue(
            String purpose,
            String templateCode,
            String emailNorm,
            String browserBinding,
            Long subjectUserId,
            Long issuedAuthVersion) {

        if (browserBinding == null || browserBinding.isBlank()) {
            throw new IllegalArgumentException("browserBinding is required");
        }

        VedioaiAuthProperties.Challenge challenge = properties.getChallenge();
        String challengeUid = "chl_" + randomToken(24);
        String code = otp(challenge.getOtpLength());
        byte[] otpMac = challengeCrypto.hmacChallenge(purpose, challengeUid, emailNorm, code);
        byte[] bindingMac = challengeCrypto.hmacBinding(browserBinding);

        String messageKey = challengeUid + ":" + templateCode;
        MailPayloadCipher.EncryptedPayload encrypted =
                mailPayloadCipher.encrypt(messageKey, code);

        jdbcTemplate.update(
                """
                INSERT INTO auth_challenge_channel
                    (purpose, email_norm, browser_binding_mac, generation, next_send_at, expires_at)
                VALUES
                    (?, ?, ?, 0, UTC_TIMESTAMP(3), DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 1 DAY))
                ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)
                """,
                purpose,
                emailNorm,
                bindingMac);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, generation,
                       TIMESTAMPDIFF(SECOND, UTC_TIMESTAMP(3), next_send_at) AS wait_seconds
                FROM auth_challenge_channel
                WHERE purpose = ? AND email_norm = ? AND browser_binding_mac = ?
                FOR UPDATE
                """,
                purpose,
                emailNorm,
                bindingMac);

        if (rows.isEmpty()) {
            throw new IllegalStateException("challenge channel was not created");
        }

        Map<String, Object> channel = rows.get(0);
        long channelId = ((Number) channel.get("id")).longValue();
        long generation = ((Number) channel.get("generation")).longValue() + 1;
        Number waitSeconds = (Number) channel.get("wait_seconds");
        if (waitSeconds != null && waitSeconds.longValue() > 0) {
            throw new ChallengeResendTooSoonException(waitSeconds.longValue());
        }

        jdbcTemplate.update(
                """
                UPDATE auth_verification_challenge
                SET status = 'INVALIDATED',
                    version = version + 1,
                    updated_at = UTC_TIMESTAMP(3)
                WHERE channel_id = ? AND status = 'PENDING'
                """,
                channelId);

        jdbcTemplate.update(
                """
                UPDATE auth_challenge_channel
                SET generation = ?,
                    next_send_at = DATE_ADD(UTC_TIMESTAMP(3), INTERVAL ? SECOND),
                    expires_at = GREATEST(expires_at, DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 1 DAY)),
                    updated_at = UTC_TIMESTAMP(3)
                WHERE id = ?
                """,
                generation,
                (int) challenge.getResendAfter().toSeconds(),
                channelId);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO auth_verification_challenge
                        (challenge_uid, purpose, email_norm, channel_id, generation,
                         subject_user_id, issued_auth_version, eligible,
                         otp_mac, mac_key_id, browser_binding_mac, attempt_count, max_attempts,
                         status, version, expires_at)
                    VALUES
                        (?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, 0, ?, 'PENDING', 1,
                         DATE_ADD(UTC_TIMESTAMP(3), INTERVAL ? SECOND))
                    """,
                    Statement.RETURN_GENERATED_KEYS);

            statement.setString(1, challengeUid);
            statement.setString(2, purpose);
            statement.setString(3, emailNorm);
            statement.setLong(4, channelId);
            statement.setLong(5, generation);
            if (subjectUserId == null) {
                statement.setNull(6, java.sql.Types.BIGINT);
            } else {
                statement.setLong(6, subjectUserId);
            }
            if (issuedAuthVersion == null) {
                statement.setNull(7, java.sql.Types.BIGINT);
            } else {
                statement.setLong(7, issuedAuthVersion);
            }
            statement.setBytes(8, otpMac);
            statement.setString(9, challengeCrypto.getHmacKeyId());
            statement.setBytes(10, bindingMac);
            statement.setInt(11, challenge.getMaxAttempts());
            statement.setInt(12, (int) challenge.getTtl().toSeconds());
            return statement;
        }, keyHolder);

        Number generatedKey = keyHolder.getKey();
        if (generatedKey == null) {
            throw new IllegalStateException("challenge id was not generated");
        }
        long challengeId = generatedKey.longValue();

        jdbcTemplate.update(
                """
                INSERT INTO auth_mail_outbox
                    (message_key, challenge_id, recipient_email_norm, template_code,
                     payload_cipher, cipher_nonce, cipher_key_id, status, attempt_no,
                     max_attempts, next_run_at, expires_at)
                VALUES
                    (?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, 5,
                     UTC_TIMESTAMP(3), DATE_ADD(UTC_TIMESTAMP(3), INTERVAL ? SECOND))
                """,
                messageKey,
                challengeId,
                emailNorm,
                templateCode,
                encrypted.cipher(),
                encrypted.nonce(),
                mailPayloadCipher.getKeyId(),
                (int) challenge.getTtl().toSeconds());

        return new ChallengeReceipt(
                challengeUid,
                challenge.getTtl().toSeconds(),
                challenge.getResendAfter().toSeconds());
    }

    public ChallengeReceipt syntheticReceipt() {
        VedioaiAuthProperties.Challenge challenge = properties.getChallenge();
        return new ChallengeReceipt(
                "chl_" + randomToken(24),
                challenge.getTtl().toSeconds(),
                challenge.getResendAfter().toSeconds());
    }

    private String otp(int length) {
        long bound = (long) Math.pow(10, length);
        long value = secureRandom.nextLong(bound);
        return String.format("%0" + length + "d", value);
    }

    private String randomToken(int byteCount) {
        byte[] bytes = new byte[byteCount];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
