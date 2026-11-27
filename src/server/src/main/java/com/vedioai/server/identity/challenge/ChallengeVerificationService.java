package com.vedioai.server.identity.challenge;

import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChallengeVerificationService {

    private static final String PURPOSE_REGISTER = "REGISTER";

    private final JdbcTemplate jdbcTemplate;
    private final ChallengeCrypto challengeCrypto;

    public ChallengeVerificationService(JdbcTemplate jdbcTemplate, ChallengeCrypto challengeCrypto) {
        this.jdbcTemplate = jdbcTemplate;
        this.challengeCrypto = challengeCrypto;
    }

    @Transactional(noRollbackFor = ChallengeInvalidException.class)
    public ChallengeVerification verify(String challengeUid, String code, String browserBinding) {
        if (challengeUid == null || challengeUid.isBlank()
                || code == null || code.isBlank()
                || browserBinding == null || browserBinding.isBlank()) {
            throw new ChallengeInvalidException();
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, email_norm, channel_id, generation, status, version,
                       otp_mac, browser_binding_mac, attempt_count, max_attempts,
                       (expires_at > UTC_TIMESTAMP(3)) AS not_expired
                FROM auth_verification_challenge
                WHERE challenge_uid = ?
                FOR UPDATE
                """,
                challengeUid);

        if (rows.isEmpty()) {
            throw new ChallengeInvalidException();
        }

        Map<String, Object> row = rows.get(0);
        long challengeId = ((Number) row.get("id")).longValue();
        long channelId = ((Number) row.get("channel_id")).longValue();
        long generation = ((Number) row.get("generation")).longValue();
        long version = ((Number) row.get("version")).longValue();
        String emailNorm = (String) row.get("email_norm");
        String status = (String) row.get("status");
        boolean notExpired = ((Number) row.get("not_expired")).intValue() == 1;

        if (!"PENDING".equals(status) || !notExpired) {
            throw new ChallengeInvalidException();
        }

        byte[] expectedBinding = challengeCrypto.hmacBinding(browserBinding);
        byte[] storedBinding = (byte[]) row.get("browser_binding_mac");
        if (!MessageDigest.isEqual(expectedBinding, storedBinding)) {
            throw new ChallengeInvalidException();
        }

        Long channelGeneration;
        try {
            channelGeneration = jdbcTemplate.queryForObject(
                    "SELECT generation FROM auth_challenge_channel WHERE id = ?",
                    Long.class,
                    channelId);
        } catch (EmptyResultDataAccessException exception) {
            throw new ChallengeInvalidException();
        }
        if (channelGeneration == null || channelGeneration != generation) {
            throw new ChallengeInvalidException();
        }

        byte[] expectedMac = challengeCrypto.hmacChallenge(
                PURPOSE_REGISTER, challengeUid, emailNorm, code);
        byte[] storedMac = (byte[]) row.get("otp_mac");
        if (!MessageDigest.isEqual(expectedMac, storedMac)) {
            int attemptCount = ((Number) row.get("attempt_count")).intValue() + 1;
            int maxAttempts = ((Number) row.get("max_attempts")).intValue();
            String nextStatus = attemptCount >= maxAttempts ? "INVALIDATED" : "PENDING";
            jdbcTemplate.update(
                    """
                    UPDATE auth_verification_challenge
                    SET attempt_count = ?,
                        status = ?,
                        version = version + 1,
                        updated_at = UTC_TIMESTAMP(3)
                    WHERE id = ? AND status = 'PENDING'
                    """,
                    attemptCount,
                    nextStatus,
                    challengeId);
            throw new ChallengeInvalidException();
        }

        return new ChallengeVerification(
                challengeId,
                challengeUid,
                emailNorm,
                channelId,
                generation,
                version);
    }
}