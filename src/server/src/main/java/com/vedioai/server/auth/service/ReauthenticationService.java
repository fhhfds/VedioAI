package com.vedioai.server.auth.service;

import com.vedioai.server.auth.config.VedioaiAuthProperties;
import com.vedioai.server.auth.exception.CurrentPasswordInvalidException;
import com.vedioai.server.auth.exception.ReauthenticationRequiredException;
import com.vedioai.server.auth.security.PasswordService;
import com.vedioai.server.auth.security.SessionGuardService;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;


@Service
public class ReauthenticationService {

    public static final String PROOF_ATTRIBUTE =
            ReauthenticationService.class.getName() + ".validUntil";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordService passwordService;
    private final SessionGuardService sessionGuardService;
    private final VedioaiAuthProperties properties;

    public ReauthenticationService(
            JdbcTemplate jdbcTemplate,
            PasswordService passwordService,
            SessionGuardService sessionGuardService,
            VedioaiAuthProperties properties) {

        this.jdbcTemplate = jdbcTemplate;
        this.passwordService = passwordService;
        this.sessionGuardService = sessionGuardService;
        this.properties = properties;
    }

    public Instant reauthenticate(
            long userId,
            String sessionUid,
            String frameworkSessionId,
            String password) {

        Map<String, Object> credential;
        try {
            credential = jdbcTemplate.queryForMap(
                    "SELECT password_hash FROM auth_password_credential WHERE user_id = ?",
                    userId);
        } catch (EmptyResultDataAccessException exception) {
            throw new CurrentPasswordInvalidException();
        }

        String passwordHash = (String) credential.get("password_hash");
        if (!passwordService.matches(password, passwordHash)) {
            throw new CurrentPasswordInvalidException();
        }

        sessionGuardService.requireActive(userId, sessionUid, frameworkSessionId);

        Instant validUntil = Instant.now().plus(properties.getReauthentication().getTtl());
        jdbcTemplate.update(
                """
                UPDATE auth_session_guard
                SET last_reauth_at = UTC_TIMESTAMP(3), updated_at = UTC_TIMESTAMP(3)
                WHERE user_id = ? AND session_uid = ? AND status = 'ACTIVE'
                """,
                userId,
                sessionUid);
        return validUntil;
    }

    public void mark(HttpSession session, Instant validUntil) {
        session.setAttribute(PROOF_ATTRIBUTE, validUntil.toString());
    }

    public Instant validUntil(HttpSession session) {
        Object value = session.getAttribute(PROOF_ATTRIBUTE);
        if (value instanceof String text) {
            try {
                return Instant.parse(text);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    public boolean isValid(HttpSession session) {
        Instant validUntil = validUntil(session);
        return validUntil != null && validUntil.isAfter(Instant.now());
    }

    public void require(HttpSession session) {
        if (!isValid(session)) {
            throw new ReauthenticationRequiredException();
        }
    }

    public void clear(HttpSession session) {
        session.removeAttribute(PROOF_ATTRIBUTE);
    }
}
