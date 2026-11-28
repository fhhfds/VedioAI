package com.vedioai.server.auth.service;

import com.vedioai.server.auth.exception.AccountDisabledException;
import com.vedioai.server.auth.exception.SessionExpiredException;
import com.vedioai.server.auth.security.SessionGuardService;
import com.vedioai.server.auth.util.IdentityIds;
import com.vedioai.server.common.web.RequestMetadata;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class AccountSessionService {

    private final JdbcTemplate jdbcTemplate;
    private final SessionGuardService sessionGuardService;

    public AccountSessionService(
            JdbcTemplate jdbcTemplate,
            SessionGuardService sessionGuardService) {

        this.jdbcTemplate = jdbcTemplate;
        this.sessionGuardService = sessionGuardService;
    }

    @Transactional
    public void revokeAll(
            long userId,
            String sessionUid,
            String frameworkSessionId,
            RequestMetadata metadata) {

        Map<String, Object> user;
        try {
            user = jdbcTemplate.queryForMap(
                    """
                    SELECT status, auth_version, version
                    FROM auth_user
                    WHERE id = ?
                    FOR UPDATE
                    """,
                    userId);
        } catch (EmptyResultDataAccessException exception) {
            throw new SessionExpiredException();
        }

        if (!"ACTIVE".equals(user.get("status"))) {
            throw new AccountDisabledException();
        }

        sessionGuardService.requireActive(userId, sessionUid, frameworkSessionId);

        jdbcTemplate.update(
                """
                UPDATE auth_user
                SET auth_version = auth_version + 1,
                    version = version + 1,
                    updated_at = UTC_TIMESTAMP(3)
                WHERE id = ?
                """,
                userId);

        sessionGuardService.revokeAll(userId, "ALL");

        jdbcTemplate.update(
                """
                INSERT INTO auth_audit_event
                    (event_uid, actor_user_id, subject_user_id, session_uid, event_type,
                     outcome, reason_code, identifier_hmac, ip_prefix, user_agent_label,
                     trace_id, context_json)
                VALUES
                    (?, ?, ?, ?, 'SESSION_REVOKE_ALL', 'SUCCESS', NULL, NULL, ?, ?, ?, NULL)
                """,
                IdentityIds.eventUid(),
                userId,
                userId,
                sessionUid,
                safe(metadata == null ? null : metadata.ipPrefix(), "unknown", 64),
                safe(metadata == null ? null : metadata.userAgentLabel(), "unknown", 160),
                safe(metadata == null ? null : metadata.traceId(), "unknown", 128));
    }

    private static String safe(String value, String fallback, int maxLength) {
        String result = value == null || value.isBlank() ? fallback : value;
        return result.length() <= maxLength ? result : result.substring(0, maxLength);
    }
}
