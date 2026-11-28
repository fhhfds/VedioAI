package com.vedioai.server.auth.challenge;

import com.vedioai.server.auth.util.EmailNormalizer;
import org.springframework.stereotype.Service;


@Service
public class RegistrationChallengeService {

    private static final String PURPOSE_REGISTER = "REGISTER";
    private static final String TEMPLATE_REGISTER_CODE = "REGISTER_CODE";

    private final EmailChallengeIssuer emailChallengeIssuer;

    public RegistrationChallengeService(EmailChallengeIssuer emailChallengeIssuer) {
        this.emailChallengeIssuer = emailChallengeIssuer;
    }

    public ChallengeReceipt request(String rawEmail, String browserBinding) {
        String emailNorm = EmailNormalizer.normalize(rawEmail);
        return emailChallengeIssuer.issue(
                PURPOSE_REGISTER,
                TEMPLATE_REGISTER_CODE,
                emailNorm,
                browserBinding,
                null,
                null);
    }
}
