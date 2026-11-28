package com.vedioai.server.auth.service;

import com.vedioai.server.auth.exception.CurrentPasswordInvalidException;
import com.vedioai.server.auth.repository.PasswordPersistenceService;
import com.vedioai.server.auth.security.PasswordPolicy;
import com.vedioai.server.auth.security.PasswordService;
import com.vedioai.server.common.web.RequestMetadata;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;


@Service
public class PasswordManagementService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordService passwordService;
    private final PasswordPolicy passwordPolicy;
    private final PasswordPersistenceService passwordPersistenceService;

    public PasswordManagementService(
            JdbcTemplate jdbcTemplate,
            PasswordService passwordService,
            PasswordPolicy passwordPolicy,
            PasswordPersistenceService passwordPersistenceService) {

        this.jdbcTemplate = jdbcTemplate;
        this.passwordService = passwordService;
        this.passwordPolicy = passwordPolicy;
        this.passwordPersistenceService = passwordPersistenceService;
    }

    public void change(
            long userId,
            String sessionUid,
            String frameworkSessionId,
            String currentPassword,
            String newPassword,
            RequestMetadata metadata) {

        Map<String, Object> credential;
        try {
            credential = jdbcTemplate.queryForMap(
                    "SELECT password_hash FROM auth_password_credential WHERE user_id = ?",
                    userId);
        } catch (EmptyResultDataAccessException exception) {
            throw new CurrentPasswordInvalidException();
        }

        String currentHash = (String) credential.get("password_hash");
        if (!passwordService.matches(currentPassword, currentHash)) {
            throw new CurrentPasswordInvalidException();
        }

        passwordPolicy.validate(newPassword);
        String newHash = passwordService.encode(newPassword);

        passwordPersistenceService.change(
                userId,
                sessionUid,
                frameworkSessionId,
                currentHash,
                newHash,
                metadata);
    }
}
