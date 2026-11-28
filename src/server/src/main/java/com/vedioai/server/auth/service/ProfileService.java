package com.vedioai.server.auth.service;

import com.vedioai.server.auth.dto.AuthUserView;
import com.vedioai.server.auth.dto.AuthUserViews;
import com.vedioai.server.auth.exception.AccountDisabledException;
import com.vedioai.server.auth.exception.SessionExpiredException;
import com.vedioai.server.auth.exception.VersionConflictException;
import com.vedioai.server.auth.util.IdentityIds;
import com.vedioai.server.auth.util.NicknamePolicy;
import com.vedioai.server.common.web.RequestMetadata;
import java.util.List;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class ProfileService {

    private final JdbcTemplate jdbcTemplate;

    public ProfileService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public AuthUserView updateNickname(
            long userId,
            String nickname,
            long expectedVersion,
            RequestMetadata metadata) {

        String normalizedNickname = NicknamePolicy.normalize(nickname);

        Map<String, Object> user;
        try {
            user = jdbcTemplate.queryForMap(
                    """
                    SELECT id, user_uid, email_norm, nickname, status, auth_version,
                           version, created_at, email_verified_at
                    FROM auth_user
                    WHERE id = ?
                    FOR UPDATE
                    """,
                    userId);
        } catch (EmptyResultDataAccessException exception) {
            throw new SessionExpiredException();
        }

        String status = (String) user.get("status");
        if (!"ACTIVE".equals(status)) {
            throw new AccountDisabledException();
        }

        long version = ((Number) user.get("version")).longValue();
        if (version != expectedVersion) {
            throw new VersionConflictException();
        }

        jdbcTemplate.update(
                """
                UPDATE auth_user
                SET nickname = ?, version = version + 1, updated_at = UTC_TIMESTAMP(3)
                WHERE id = ?
                """,
                normalizedNickname,
                userId);

        List<String> roles = roles(userId);

        jdbcTemplate.update(
                """
                INSERT INTO auth_audit_event
                    (event_uid, actor_user_id, subject_user_id, session_uid, event_type,
                     outcome, reason_code, identifier_hmac, ip_prefix, user_agent_label,
                     trace_id, context_json)
                VALUES
                    (?, ?, ?, NULL, 'PROFILE_UPDATE', 'SUCCESS', NULL, NULL, ?, ?, ?, NULL)
                """,
                IdentityIds.eventUid(),
                userId,
                userId,
                safe(metadata == null ? null : metadata.ipPrefix(), "unknown", 64),
                safe(metadata == null ? null : metadata.userAgentLabel(), "unknown", 160),
                safe(metadata == null ? null : metadata.traceId(), "unknown", 128));

        return AuthUserViews.of(
                (String) user.get("user_uid"),
                (String) user.get("email_norm"),
                normalizedNickname,
                status,
                user.get("email_verified_at") != null,
                roles,
                version + 1,
                AuthUserViews.instantString(user.get("created_at")));
    }

    private List<String> roles(long userId) {
        return jdbcTemplate.queryForList(
                """
                SELECT r.role_code
                FROM auth_user_role ur
                JOIN auth_role r ON r.id = ur.role_id
                WHERE ur.user_id = ?
                ORDER BY r.role_code
                """,
                String.class,
                userId);
    }

    private static String safe(String value, String fallback, int maxLength) {
        String result = value == null || value.isBlank() ? fallback : value;
        return result.length() <= maxLength ? result : result.substring(0, maxLength);
    }
}
