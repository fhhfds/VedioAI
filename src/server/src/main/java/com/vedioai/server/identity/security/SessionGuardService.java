package com.vedioai.server.identity.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SessionGuardService {

    private final JdbcTemplate jdbcTemplate;

    public SessionGuardService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void assertActive(long userId, String frameworkSessionId, long authVersion) {
        byte[] sessionHash = sha256(frameworkSessionId);

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM auth_session_guard
                WHERE user_id = ?
                  AND session_id_sha256 = ?
                  AND auth_version = ?
                  AND status = 'ACTIVE'
                """,
                Integer.class,
                userId,
                sessionHash,
                authVersion);

        if (count == null || count != 1) {
            throw new SessionGuardException("会话已失效");
        }
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}