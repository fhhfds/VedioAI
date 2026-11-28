package com.vedioai.server.auth.service;

import com.vedioai.server.auth.dto.AuthStateResponse;
import com.vedioai.server.auth.dto.AuthUserViews;
import com.vedioai.server.auth.dto.SessionView;
import com.vedioai.server.auth.exception.SessionExpiredException;
import com.vedioai.server.auth.security.SessionGuardService;
import com.vedioai.server.auth.security.SessionGuardView;
import java.util.List;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class MeService {

    private final JdbcTemplate jdbcTemplate;
    private final SessionGuardService sessionGuardService;

    public MeService(JdbcTemplate jdbcTemplate, SessionGuardService sessionGuardService) {
        this.jdbcTemplate = jdbcTemplate;
        this.sessionGuardService = sessionGuardService;
    }

    @Transactional(noRollbackFor = SessionExpiredException.class)
    public AuthStateResponse me(long userId, String frameworkSessionId) {
        Map<String, Object> user;
        try {
            user = jdbcTemplate.queryForMap(
                    """
                    SELECT id, user_uid, email_norm, nickname, status, auth_version,
                           version, created_at, email_verified_at
                    FROM auth_user
                    WHERE id = ?
                    """,
                    userId);
        } catch (EmptyResultDataAccessException exception) {
            throw new SessionExpiredException();
        }

        String status = (String) user.get("status");
        if (!"ACTIVE".equals(status)) {
            sessionGuardService.revoke(userId, frameworkSessionId, "STATUS");
            throw new SessionExpiredException();
        }

        SessionGuardView guard = sessionGuardService.activate(userId, frameworkSessionId);
        long authVersion = ((Number) user.get("auth_version")).longValue();
        if (guard.authVersion() != authVersion) {
            sessionGuardService.revoke(userId, frameworkSessionId, "VERSION_MISMATCH");
            throw new SessionExpiredException();
        }

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

        return new AuthStateResponse(
                AuthUserViews.of(
                        (String) user.get("user_uid"),
                        (String) user.get("email_norm"),
                        (String) user.get("nickname"),
                        status,
                        user.get("email_verified_at") != null,
                        roles,
                        ((Number) user.get("version")).longValue(),
                        AuthUserViews.instantString(user.get("created_at"))),
                new SessionView(
                        guard.sessionUid(),
                        guard.authenticatedAt(),
                        guard.absoluteExpiresAt()));
    }
}
