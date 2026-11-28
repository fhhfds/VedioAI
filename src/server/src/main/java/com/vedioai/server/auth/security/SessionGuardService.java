package com.vedioai.server.auth.security;

import com.vedioai.server.auth.exception.SessionGuardException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class SessionGuardService {

    private final JdbcTemplate jdbcTemplate;

    public SessionGuardService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public SessionGuardView createPending(
            long userId,
            String sessionUid,
            String frameworkSessionId,
            long authVersion,
            long pendingTtlSeconds,
            long absoluteTtlSeconds,
            String userAgentLabel,
            String ipPrefix) {

        Instant now = Instant.now();
        byte[] sessionHash = sha256(frameworkSessionId);

        jdbcTemplate.update(
                """
                INSERT INTO auth_session_guard
                    (session_uid, user_id, session_id_sha256, auth_version, status,
                     authenticated_at, pending_expires_at, absolute_expires_at,
                     last_reauth_at, last_seen_at, user_agent_label, ip_prefix)
                VALUES
                    (?, ?, ?, ?, 'PENDING',
                     UTC_TIMESTAMP(3),
                     DATE_ADD(UTC_TIMESTAMP(3), INTERVAL ? SECOND),
                     DATE_ADD(UTC_TIMESTAMP(3), INTERVAL ? SECOND),
                     UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), ?, ?)
                """,
                sessionUid,
                userId,
                sessionHash,
                authVersion,
                pendingTtlSeconds,
                absoluteTtlSeconds,
                safe(userAgentLabel, "unknown", 160),
                safe(ipPrefix, "unknown", 64));

        return new SessionGuardView(
                sessionUid,
                now.toString(),
                now.plusSeconds(absoluteTtlSeconds).toString(),
                authVersion);
    }

    @Transactional
    public SessionGuardView activate(long userId, String frameworkSessionId) {
        byte[] sessionHash = sha256(frameworkSessionId);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, session_uid, auth_version, status, authenticated_at,
                       absolute_expires_at,
                       (absolute_expires_at > UTC_TIMESTAMP(3)) AS not_expired,
                       (pending_expires_at > UTC_TIMESTAMP(3)) AS pending_valid
                FROM auth_session_guard
                WHERE user_id = ? AND session_id_sha256 = ?
                FOR UPDATE
                """,
                userId,
                sessionHash);

        if (rows.isEmpty()) {
            throw new SessionGuardException("会话已失效");
        }

        Map<String, Object> row = rows.get(0);
        long id = ((Number) row.get("id")).longValue();
        String status = (String) row.get("status");
        boolean notExpired = ((Number) row.get("not_expired")).intValue() == 1;
        boolean pendingValid = ((Number) row.get("pending_valid")).intValue() == 1;

        if ("REVOKED".equals(status) || "EXPIRED".equals(status) || !notExpired) {
            jdbcTemplate.update(
                    """
                    UPDATE auth_session_guard
                    SET status = 'EXPIRED', updated_at = UTC_TIMESTAMP(3)
                    WHERE id = ? AND status <> 'REVOKED'
                    """,
                    id);
            throw new SessionGuardException("会话已过期或已撤销");
        }

        if ("PENDING".equals(status)) {
            if (!pendingValid) {
                jdbcTemplate.update(
                        """
                        UPDATE auth_session_guard
                        SET status = 'EXPIRED', updated_at = UTC_TIMESTAMP(3)
                        WHERE id = ? AND status = 'PENDING'
                        """,
                        id);
                throw new SessionGuardException("会话占位已过期");
            }
            jdbcTemplate.update(
                    """
                    UPDATE auth_session_guard
                    SET status = 'ACTIVE',
                        activated_at = UTC_TIMESTAMP(3),
                        last_seen_at = UTC_TIMESTAMP(3),
                        updated_at = UTC_TIMESTAMP(3)
                    WHERE id = ? AND status = 'PENDING'
                    """,
                    id);
        } else {
            jdbcTemplate.update(
                    """
                    UPDATE auth_session_guard
                    SET last_seen_at = UTC_TIMESTAMP(3), updated_at = UTC_TIMESTAMP(3)
                    WHERE id = ?
                    """,
                    id);
        }

        return new SessionGuardView(
                (String) row.get("session_uid"),
                toInstantString(row.get("authenticated_at")),
                toInstantString(row.get("absolute_expires_at")),
                ((Number) row.get("auth_version")).longValue());
    }

    @Transactional
    public void revoke(long userId, String frameworkSessionId, String reason) {
        byte[] sessionHash = sha256(frameworkSessionId);
        jdbcTemplate.update(
                """
                UPDATE auth_session_guard
                SET status = 'REVOKED',
                    revoked_at = UTC_TIMESTAMP(3),
                    revocation_reason = ?,
                    updated_at = UTC_TIMESTAMP(3)
                WHERE user_id = ?
                  AND session_id_sha256 = ?
                  AND status IN ('PENDING', 'ACTIVE')
                """,
                safe(reason, "REVOKE", 32),
                userId,
                sessionHash);
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

    /**
     * Validates the framework session against the guard ledger and the current user state.
     * This is the source of truth for every authenticated request.
     */
    @Transactional
    public SessionGuardRecord requireActive(
            long userId,
            String sessionUid,
            String frameworkSessionId) {

        byte[] sessionHash = sha256(frameworkSessionId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT g.id, g.session_uid, g.user_id, g.auth_version, g.status,
                       g.created_at, g.authenticated_at, g.absolute_expires_at,
                       g.last_seen_at, g.user_agent_label, g.ip_prefix,
                       (g.absolute_expires_at > UTC_TIMESTAMP(3)) AS not_expired,
                       u.status AS user_status,
                       u.auth_version AS user_auth_version
                FROM auth_session_guard g
                JOIN auth_user u ON u.id = g.user_id
                WHERE g.user_id = ?
                  AND g.session_uid = ?
                  AND g.session_id_sha256 = ?
                FOR UPDATE
                """,
                userId,
                sessionUid,
                sessionHash);

        if (rows.isEmpty()) {
            throw new SessionGuardException("会话已失效");
        }

        Map<String, Object> row = rows.get(0);
        String status = (String) row.get("status");
        boolean notExpired = ((Number) row.get("not_expired")).intValue() == 1;
        long guardVersion = ((Number) row.get("auth_version")).longValue();
        long userVersion = ((Number) row.get("user_auth_version")).longValue();
        String userStatus = (String) row.get("user_status");

        if (!"ACTIVE".equals(status)
                || !notExpired
                || !"ACTIVE".equals(userStatus)
                || guardVersion != userVersion) {
            jdbcTemplate.update(
                    """
                    UPDATE auth_session_guard
                    SET status = 'REVOKED',
                        revoked_at = UTC_TIMESTAMP(3),
                        revocation_reason = 'STALE',
                        updated_at = UTC_TIMESTAMP(3)
                    WHERE id = ? AND status IN ('PENDING','ACTIVE')
                    """,
                    ((Number) row.get("id")).longValue());
            throw new SessionGuardException("会话已失效");
        }

        jdbcTemplate.update(
                """
                UPDATE auth_session_guard
                SET last_seen_at = UTC_TIMESTAMP(3), updated_at = UTC_TIMESTAMP(3)
                WHERE id = ?
                """,
                ((Number) row.get("id")).longValue());

        return new SessionGuardRecord(
                ((Number) row.get("id")).longValue(),
                (String) row.get("session_uid"),
                ((Number) row.get("user_id")).longValue(),
                guardVersion,
                status,
                toInstant(row.get("created_at")),
                toInstant(row.get("authenticated_at")),
                toInstant(row.get("absolute_expires_at")),
                toInstant(row.get("last_seen_at")),
                (String) row.get("user_agent_label"),
                (String) row.get("ip_prefix"));
    }

    public List<SessionGuardRecord> listActive(long userId) {
        return jdbcTemplate.query(
                """
                SELECT id, session_uid, user_id, auth_version, status, created_at,
                       authenticated_at, absolute_expires_at, last_seen_at,
                       user_agent_label, ip_prefix
                FROM auth_session_guard
                WHERE user_id = ?
                  AND status IN ('PENDING','ACTIVE')
                  AND absolute_expires_at > UTC_TIMESTAMP(3)
                ORDER BY created_at DESC, id DESC
                """,
                (rs, rowNum) -> new SessionGuardRecord(
                        rs.getLong("id"),
                        rs.getString("session_uid"),
                        rs.getLong("user_id"),
                        rs.getLong("auth_version"),
                        rs.getString("status"),
                        toInstant(rs.getTimestamp("created_at")),
                        toInstant(rs.getTimestamp("authenticated_at")),
                        toInstant(rs.getTimestamp("absolute_expires_at")),
                        toInstant(rs.getTimestamp("last_seen_at")),
                        rs.getString("user_agent_label"),
                        rs.getString("ip_prefix")),
                userId);
    }

    @Transactional
    public int revokeAll(long userId, String reason) {
        return jdbcTemplate.update(
                """
                UPDATE auth_session_guard
                SET status = 'REVOKED',
                    revoked_at = UTC_TIMESTAMP(3),
                    revocation_reason = ?,
                    updated_at = UTC_TIMESTAMP(3)
                WHERE user_id = ? AND status IN ('PENDING','ACTIVE')
                """,
                safe(reason, "ALL", 32),
                userId);
    }

    @Transactional
    public boolean revokeByUid(long userId, String sessionUid, String reason) {
        int updated = jdbcTemplate.update(
                """
                UPDATE auth_session_guard
                SET status = 'REVOKED',
                    revoked_at = UTC_TIMESTAMP(3),
                    revocation_reason = ?,
                    updated_at = UTC_TIMESTAMP(3)
                WHERE user_id = ?
                  AND session_uid = ?
                  AND status IN ('PENDING','ACTIVE')
                """,
                safe(reason, "REVOKE", 32),
                userId,
                sessionUid);
        return updated == 1;
    }

    public boolean existsActiveByUid(long userId, String sessionUid) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM auth_session_guard
                WHERE user_id = ? AND session_uid = ?
                  AND status IN ('PENDING','ACTIVE')
                  AND absolute_expires_at > UTC_TIMESTAMP(3)
                """,
                Integer.class,
                userId,
                sessionUid);
        return count != null && count == 1;
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toInstant(ZoneOffset.UTC);
        }
        return Instant.EPOCH;
    }

    private static String toInstantString(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toInstant(ZoneOffset.UTC).toString();
        }
        return value == null ? Instant.EPOCH.toString() : value.toString();
    }

    private static String safe(String value, String fallback, int maxLength) {
        String result = value == null || value.isBlank() ? fallback : value;
        return result.length() <= maxLength ? result : result.substring(0, maxLength);
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
