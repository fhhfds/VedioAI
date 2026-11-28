package com.vedioai.server.auth.service;

import com.vedioai.server.auth.domain.AuthenticatedUser;
import com.vedioai.server.auth.dto.AuthUserViews;
import com.vedioai.server.auth.exception.AccountDisabledException;
import com.vedioai.server.auth.exception.InvalidCredentialsException;
import com.vedioai.server.auth.exception.InvalidEmailException;
import com.vedioai.server.auth.security.PasswordService;
import com.vedioai.server.auth.util.EmailNormalizer;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;


@Service
public class AuthLoginService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordService passwordService;
    private final String dummyPasswordHash;

    public AuthLoginService(JdbcTemplate jdbcTemplate, PasswordService passwordService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordService = passwordService;
        this.dummyPasswordHash = passwordService.encode(
                "timing-equalization-placeholder-for-unknown-accounts");
    }

    public AuthenticatedUser authenticate(String rawEmail, String password) {
        String email;
        try {
            email = EmailNormalizer.normalize(rawEmail);
        } catch (InvalidEmailException exception) {
            passwordService.matches(password, dummyPasswordHash);
            throw new InvalidCredentialsException();
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT u.id, u.user_uid, u.email_norm, u.nickname, u.status,
                       u.auth_version, u.version, u.created_at, u.email_verified_at,
                       c.password_hash, c.credential_version
                FROM auth_user u
                LEFT JOIN auth_password_credential c ON c.user_id = u.id
                WHERE u.email_norm = ?
                """,
                email);

        if (rows.isEmpty()) {
            passwordService.matches(password, dummyPasswordHash);
            throw new InvalidCredentialsException();
        }

        Map<String, Object> row = rows.get(0);
        String passwordHash = (String) row.get("password_hash");
        if (passwordHash == null) {
            passwordService.matches(password, dummyPasswordHash);
            throw new InvalidCredentialsException();
        }

        if (!passwordService.matches(password, passwordHash)) {
            throw new InvalidCredentialsException();
        }

        String status = (String) row.get("status");
        if (!"ACTIVE".equals(status)) {
            throw new AccountDisabledException();
        }

        long userId = ((Number) row.get("id")).longValue();
        List<String> roles = jdbcTemplate.queryForList(
                """
                SELECT r.role_code
                FROM auth_user_role ur
                JOIN auth_role r ON r.id = ur.role_id
                WHERE ur.user_id = ?
                ORDER BY r.role_code
                """,
                String.class,
                userId);

        return new AuthenticatedUser(
                userId,
                (String) row.get("user_uid"),
                (String) row.get("email_norm"),
                (String) row.get("nickname"),
                status,
                ((Number) row.get("auth_version")).longValue(),
                ((Number) row.get("version")).longValue(),
                AuthUserViews.instantString(row.get("created_at")),
                row.get("email_verified_at") != null,
                roles,
                passwordHash,
                ((Number) row.get("credential_version")).longValue());
    }
}
