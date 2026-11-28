package com.vedioai.server.auth.service;

import com.vedioai.server.auth.challenge.ChallengeVerification;
import com.vedioai.server.auth.challenge.ChallengeVerificationService;
import com.vedioai.server.auth.exception.ChallengeInvalidException;
import com.vedioai.server.auth.repository.PasswordPersistenceService;
import com.vedioai.server.auth.security.PasswordPolicy;
import com.vedioai.server.auth.security.PasswordService;
import com.vedioai.server.common.web.RequestMetadata;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;


@Service
public class PasswordResetService {

    private static final String PURPOSE_RESET = "RESET";

    private final ChallengeVerificationService challengeVerificationService;
    private final PasswordPolicy passwordPolicy;
    private final PasswordService passwordService;
    private final PasswordPersistenceService passwordPersistenceService;
    private final JdbcTemplate jdbcTemplate;

    public PasswordResetService(
            ChallengeVerificationService challengeVerificationService,
            PasswordPolicy passwordPolicy,
            PasswordService passwordService,
            PasswordPersistenceService passwordPersistenceService,
            JdbcTemplate jdbcTemplate) {

        this.challengeVerificationService = challengeVerificationService;
        this.passwordPolicy = passwordPolicy;
        this.passwordService = passwordService;
        this.passwordPersistenceService = passwordPersistenceService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void reset(
            String challengeId,
            String code,
            String newPassword,
            String browserBinding,
            RequestMetadata metadata) {

        passwordPolicy.validate(newPassword);

        ChallengeVerification verification = challengeVerificationService.verify(
                PURPOSE_RESET, challengeId, code, browserBinding);

        if (verification.subjectUserId() == null || verification.issuedAuthVersion() == null) {
            throw new ChallengeInvalidException();
        }

        Map<String, Object> user;
        try {
            user = jdbcTemplate.queryForMap(
                    "SELECT status, auth_version FROM auth_user WHERE id = ?",
                    verification.subjectUserId());
        } catch (EmptyResultDataAccessException exception) {
            throw new ChallengeInvalidException();
        }

        if (!"ACTIVE".equals(user.get("status"))
                || ((Number) user.get("auth_version")).longValue()
                        != verification.issuedAuthVersion()) {
            throw new ChallengeInvalidException();
        }

        String newHash = passwordService.encode(newPassword);

        passwordPersistenceService.reset(
                verification,
                code,
                browserBinding,
                newHash,
                metadata);
    }
}
