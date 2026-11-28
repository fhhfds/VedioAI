package com.vedioai.server.auth.challenge;

import com.vedioai.server.auth.util.EmailNormalizer;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;


@Service
public class PasswordResetChallengeService {

    private static final String PURPOSE_RESET = "RESET";
    private static final String TEMPLATE_RESET_CODE = "RESET_CODE";

    private final JdbcTemplate jdbcTemplate;
    private final EmailChallengeIssuer emailChallengeIssuer;

    public PasswordResetChallengeService(
            JdbcTemplate jdbcTemplate,
            EmailChallengeIssuer emailChallengeIssuer) {

        this.jdbcTemplate = jdbcTemplate;
        this.emailChallengeIssuer = emailChallengeIssuer;
    }

    public ChallengeReceipt request(String rawEmail, String browserBinding) {
        String emailNorm = EmailNormalizer.normalize(rawEmail);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, status, auth_version, email_verified_at
                FROM auth_user
                WHERE email_norm = ?
                """,
                emailNorm);

        if (rows.isEmpty()) {
            return emailChallengeIssuer.syntheticReceipt();
        }

        Map<String, Object> user = rows.get(0);
        String status = (String) user.get("status");
        if (!"ACTIVE".equals(status) || user.get("email_verified_at") == null) {
            return emailChallengeIssuer.syntheticReceipt();
        }

        return emailChallengeIssuer.issue(
                PURPOSE_RESET,
                TEMPLATE_RESET_CODE,
                emailNorm,
                browserBinding,
                ((Number) user.get("id")).longValue(),
                ((Number) user.get("auth_version")).longValue());
    }
}
