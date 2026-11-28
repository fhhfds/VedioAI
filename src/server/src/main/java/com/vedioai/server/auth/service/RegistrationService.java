package com.vedioai.server.auth.service;

import com.vedioai.server.auth.challenge.ChallengeVerification;
import com.vedioai.server.auth.challenge.ChallengeVerificationService;
import com.vedioai.server.auth.dto.AuthUserView;
import com.vedioai.server.auth.exception.InvalidNicknameException;
import com.vedioai.server.auth.repository.RegistrationPersistenceService;
import com.vedioai.server.auth.security.PasswordPolicy;
import com.vedioai.server.auth.security.PasswordService;
import com.vedioai.server.common.web.RequestMetadata;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Service;


@Service
public class RegistrationService {

    private final ChallengeVerificationService challengeVerificationService;
    private final RegistrationPersistenceService registrationPersistenceService;
    private final PasswordPolicy passwordPolicy;
    private final PasswordService passwordService;
    private final SecureRandom secureRandom = new SecureRandom();

    public RegistrationService(
            ChallengeVerificationService challengeVerificationService,
            RegistrationPersistenceService registrationPersistenceService,
            PasswordPolicy passwordPolicy,
            PasswordService passwordService) {

        this.challengeVerificationService = challengeVerificationService;
        this.registrationPersistenceService = registrationPersistenceService;
        this.passwordPolicy = passwordPolicy;
        this.passwordService = passwordService;
    }

    public AuthUserView register(
            String challengeId,
            String code,
            String nickname,
            String password,
            String browserBinding,
            RequestMetadata metadata) {

        String normalizedNickname = normalizeNickname(nickname);
        passwordPolicy.validate(password);

        ChallengeVerification verification = challengeVerificationService.verify(
                challengeId, code, browserBinding);

        // Argon2 runs outside the final challenge-consumption transaction.
        String passwordHash = passwordService.encode(password);
        String userUid = "usr_" + randomToken(24);

        return registrationPersistenceService.create(
                verification,
                code,
                browserBinding,
                normalizedNickname,
                passwordHash,
                userUid,
                metadata);
    }

    private String normalizeNickname(String nickname) {
        String normalized = nickname == null ? "" : nickname.trim();
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints < 1 || codePoints > 64) {
            throw new InvalidNicknameException();
        }
        return normalized;
    }

    private String randomToken(int byteCount) {
        byte[] bytes = new byte[byteCount];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
