package com.vedioai.server.auth.service;

import com.vedioai.server.auth.dto.AdminUserPageView;
import com.vedioai.server.auth.dto.AdminUserView;
import com.vedioai.server.auth.dto.AuthUserViews;
import com.vedioai.server.auth.exception.LastAdminException;
import com.vedioai.server.auth.exception.SelfModificationException;
import com.vedioai.server.auth.exception.VersionConflictException;
import com.vedioai.server.auth.security.SessionGuardService;
import com.vedioai.server.auth.util.IdentityIds;
import com.vedioai.server.common.error.ResourceNotFoundException;
import com.vedioai.server.common.util.EmailMasking;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class AdminUserService {

    private static final Set<String> ALLOWED_STATUSES = Set.of("ACTIVE", "DISABLED");
    private static final Set<String> ALLOWED_ROLES = Set.of("USER", "ADMIN");

    private final JdbcTemplate jdbcTemplate;
    private final SessionGuardService sessionGuardService;

    public AdminUserService(
            JdbcTemplate jdbcTemplate,
            SessionGuardService sessionGuardService) {

        this.jdbcTemplate = jdbcTemplate;
        this.sessionGuardService = sessionGuardService;
    }

    public AdminUserPageView list(int page, int pageSize, String status) {
        if (status != null && !ALLOWED_STATUSES.contains(status)) {
            throw new IllegalArgumentException("invalid status");
        }

        String where = status == null ? "" : " WHERE u.status = ?";
        long total;
        if (status == null) {
            Long value = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM auth_user u", Long.class);
            total = value == null ? 0 : value;
        } else {
            Long value = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM auth_user u" + where, Long.class, status);
            total = value == null ? 0 : value;
        }

        String sql = """
                SELECT u.user_uid, u.email_norm, u.nickname, u.status, u.version,
                       u.created_at,
                       GROUP_CONCAT(r.role_code ORDER BY r.role_code SEPARATOR ',') AS roles
                FROM auth_user u
                LEFT JOIN auth_user_role ur ON ur.user_id = u.id
                LEFT JOIN auth_role r ON r.id = ur.role_id
                """ + where + """
                GROUP BY u.id, u.user_uid, u.email_norm, u.nickname, u.status,
                         u.version, u.created_at
                ORDER BY u.created_at DESC, u.id DESC
                LIMIT ? OFFSET ?
                """;

        int offset = (page - 1) * pageSize;
        List<AdminUserView> items;
        if (status == null) {
            items = jdbcTemplate.query(sql, (rs, rowNum) -> mapView(
                    rs.getString("user_uid"),
                    rs.getString("email_norm"),
                    rs.getString("nickname"),
                    rs.getString("status"),
                    rs.getString("roles"),
                    rs.getLong("version"),
                    AuthUserViews.instantString(rs.getTimestamp("created_at"))),
                    pageSize, offset);
        } else {
            items = jdbcTemplate.query(sql, (rs, rowNum) -> mapView(
                    rs.getString("user_uid"),
                    rs.getString("email_norm"),
                    rs.getString("nickname"),
                    rs.getString("status"),
                    rs.getString("roles"),
                    rs.getLong("version"),
                    AuthUserViews.instantString(rs.getTimestamp("created_at"))),
                    status, pageSize, offset);
        }

        return new AdminUserPageView(items, page, pageSize, total);
    }

    @Transactional
    public AdminUserView changeStatus(
            long adminUserId,
            String targetUserUid,
            String status,
            long expectedVersion) {

        if (!ALLOWED_STATUSES.contains(status)) {
            throw new IllegalArgumentException("invalid status");
        }
        if (adminUserId == userIdOf(targetUserUid)) {
            throw new SelfModificationException();
        }

        lockAdminRole();
        Map<String, Object> target = lockUser(targetUserUid);
        long targetId = ((Number) target.get("id")).longValue();

        if (((Number) target.get("version")).longValue() != expectedVersion) {
            throw new VersionConflictException();
        }

        String previousStatus = (String) target.get("status");
        boolean targetIsActiveAdmin = "ACTIVE".equals(previousStatus)
                && hasRole(targetId, "ADMIN");

        if (targetIsActiveAdmin
                && "DISABLED".equals(status)
                && countOtherActiveAdmins(targetId) == 0) {
            throw new LastAdminException();
        }

        jdbcTemplate.update(
                """
                UPDATE auth_user
                SET status = ?,
                    disabled_at = CASE WHEN ? = 'DISABLED' THEN UTC_TIMESTAMP(3) ELSE NULL END,
                    auth_version = auth_version + 1,
                    version = version + 1,
                    updated_at = UTC_TIMESTAMP(3)
                WHERE id = ?
                """,
                status,
                status,
                targetId);

        sessionGuardService.revokeAll(targetId, "STATUS");
        audit(
                adminUserId,
                targetId,
                "ADMIN_STATUS_CHANGE",
                "{\"from\":\"" + previousStatus + "\",\"to\":\"" + status + "\"}");

        return loadView(targetUserUid);
    }

    @Transactional
    public AdminUserView changeRoles(
            long adminUserId,
            String targetUserUid,
            List<String> roles,
            long expectedVersion) {

        List<String> normalizedRoles = normalizeRoles(roles);
        if (adminUserId == userIdOf(targetUserUid)) {
            throw new SelfModificationException();
        }

        lockAdminRole();
        Map<String, Object> target = lockUser(targetUserUid);
        long targetId = ((Number) target.get("id")).longValue();

        if (((Number) target.get("version")).longValue() != expectedVersion) {
            throw new VersionConflictException();
        }

        List<String> previousRoles = rolesOf(targetId);
        boolean currentlyAdmin = previousRoles.contains("ADMIN");
        boolean nextAdmin = normalizedRoles.contains("ADMIN");

        if (currentlyAdmin
                && !nextAdmin
                && "ACTIVE".equals(target.get("status"))
                && countOtherActiveAdmins(targetId) == 0) {
            throw new LastAdminException();
        }

        long userIdRoleId = roleId("USER");
        jdbcTemplate.update("DELETE FROM auth_user_role WHERE user_id = ?", targetId);
        jdbcTemplate.update(
                "INSERT INTO auth_user_role (user_id, role_id, assigned_by_user_id) VALUES (?, ?, ?)",
                targetId,
                userIdRoleId,
                adminUserId);

        if (nextAdmin) {
            jdbcTemplate.update(
                    "INSERT INTO auth_user_role (user_id, role_id, assigned_by_user_id) VALUES (?, ?, ?)",
                    targetId,
                    roleId("ADMIN"),
                    adminUserId);
        }

        jdbcTemplate.update(
                """
                UPDATE auth_user
                SET auth_version = auth_version + 1,
                    version = version + 1,
                    updated_at = UTC_TIMESTAMP(3)
                WHERE id = ?
                """,
                targetId);

        sessionGuardService.revokeAll(targetId, "ROLE");
        audit(
                adminUserId,
                targetId,
                "ADMIN_ROLE_CHANGE",
                "{\"from\":" + jsonArray(previousRoles)
                        + ",\"to\":" + jsonArray(normalizedRoles) + "}");

        return loadView(targetUserUid);
    }

    @Transactional
    public AdminUserView bootstrapAdmin(String targetUserUid) {
        lockAdminRole();

        Map<String, Object> target = lockUser(targetUserUid);
        long targetId = ((Number) target.get("id")).longValue();

        if (!"ACTIVE".equals(target.get("status"))
                || target.get("email_verified_at") == null) {
            throw new IllegalStateException("bootstrap target must be an ACTIVE verified user");
        }

        Long activeAdmins = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT u.id)
                FROM auth_user u
                JOIN auth_user_role ur ON ur.user_id = u.id
                JOIN auth_role r ON r.id = ur.role_id
                WHERE r.role_code = 'ADMIN' AND u.status = 'ACTIVE'
                """,
                Long.class);
        if (activeAdmins != null && activeAdmins > 0) {
            throw new IllegalStateException("an active administrator already exists");
        }

        jdbcTemplate.update(
                """
                INSERT INTO auth_user_role (user_id, role_id, assigned_by_user_id)
                VALUES (?, ?, NULL)
                ON DUPLICATE KEY UPDATE role_id = role_id
                """,
                targetId,
                roleId("USER"));
        jdbcTemplate.update(
                """
                INSERT INTO auth_user_role (user_id, role_id, assigned_by_user_id)
                VALUES (?, ?, NULL)
                ON DUPLICATE KEY UPDATE role_id = role_id
                """,
                targetId,
                roleId("ADMIN"));

        jdbcTemplate.update(
                """
                UPDATE auth_user
                SET auth_version = auth_version + 1,
                    version = version + 1,
                    updated_at = UTC_TIMESTAMP(3)
                WHERE id = ?
                """,
                targetId);

        sessionGuardService.revokeAll(targetId, "BOOTSTRAP");

        jdbcTemplate.update(
                """
                INSERT INTO auth_audit_event
                    (event_uid, actor_user_id, subject_user_id, session_uid, event_type,
                     outcome, reason_code, identifier_hmac, ip_prefix, user_agent_label,
                     trace_id, context_json)
                VALUES
                    (?, NULL, ?, NULL, 'ADMIN_BOOTSTRAP', 'SUCCESS', NULL, NULL,
                     'local', 'bootstrap', ?, '{"role":"ADMIN"}')
                """,
                IdentityIds.eventUid(),
                targetId,
                "bootstrap-" + IdentityIds.randomToken(12));

        return loadView(targetUserUid);
    }

    private void lockAdminRole() {
        try {
            jdbcTemplate.queryForObject(
                    "SELECT id FROM auth_role WHERE role_code = 'ADMIN' FOR UPDATE",
                    Long.class);
        } catch (EmptyResultDataAccessException exception) {
            throw new IllegalStateException("ADMIN role is missing", exception);
        }
    }

    private long roleId(String roleCode) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM auth_role WHERE role_code = ?",
                Long.class,
                roleCode);
        if (id == null) {
            throw new IllegalStateException(roleCode + " role is missing");
        }
        return id;
    }

    private Map<String, Object> lockUser(String userUid) {
        try {
            return jdbcTemplate.queryForMap(
                    """
                    SELECT id, status, version, auth_version, email_verified_at
                    FROM auth_user
                    WHERE user_uid = ?
                    FOR UPDATE
                    """,
                    userUid);
        } catch (EmptyResultDataAccessException exception) {
            throw new ResourceNotFoundException();
        }
    }

    private long userIdOf(String userUid) {
        try {
            Long id = jdbcTemplate.queryForObject(
                    "SELECT id FROM auth_user WHERE user_uid = ?",
                    Long.class,
                    userUid);
            return id == null ? -1 : id;
        } catch (EmptyResultDataAccessException exception) {
            throw new ResourceNotFoundException();
        }
    }

    private boolean hasRole(long userId, String roleCode) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM auth_user_role ur
                JOIN auth_role r ON r.id = ur.role_id
                WHERE ur.user_id = ? AND r.role_code = ?
                """,
                Integer.class,
                userId,
                roleCode);
        return count != null && count > 0;
    }

    private List<String> rolesOf(long userId) {
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

    private long countOtherActiveAdmins(long excludedUserId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(DISTINCT u.id)
                FROM auth_user u
                JOIN auth_user_role ur ON ur.user_id = u.id
                JOIN auth_role r ON r.id = ur.role_id
                WHERE r.role_code = 'ADMIN'
                  AND u.status = 'ACTIVE'
                  AND u.id <> ?
                """,
                Long.class,
                excludedUserId);
        return count == null ? 0 : count;
    }

    private AdminUserView loadView(String userUid) {
        Map<String, Object> user;
        try {
            user = jdbcTemplate.queryForMap(
                    """
                    SELECT id, user_uid, email_norm, nickname, status, version, created_at
                    FROM auth_user
                    WHERE user_uid = ?
                    """,
                    userUid);
        } catch (EmptyResultDataAccessException exception) {
            throw new ResourceNotFoundException();
        }
        return mapView(
                (String) user.get("user_uid"),
                (String) user.get("email_norm"),
                (String) user.get("nickname"),
                (String) user.get("status"),
                String.join(",", rolesOf(((Number) user.get("id")).longValue())),
                ((Number) user.get("version")).longValue(),
                AuthUserViews.instantString(user.get("created_at")));
    }

    private AdminUserView mapView(
            String userUid,
            String email,
            String nickname,
            String status,
            String rolesCsv,
            long version,
            String createdAt) {

        List<String> roles = new ArrayList<>();
        if (rolesCsv != null && !rolesCsv.isBlank()) {
            roles.addAll(List.of(rolesCsv.split(",")));
        }
        return new AdminUserView(
                userUid,
                EmailMasking.mask(email),
                nickname,
                status,
                roles,
                version,
                createdAt);
    }

    private List<String> normalizeRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("roles are required");
        }
        Set<String> unique = new LinkedHashSet<>(roles);
        if (unique.size() != roles.size()
                || unique.size() > 2
                || !unique.contains("USER")
                || !ALLOWED_ROLES.containsAll(unique)) {
            throw new IllegalArgumentException("roles must be USER or USER+ADMIN");
        }
        return List.copyOf(unique);
    }

    private String jsonArray(List<String> values) {
        return values.stream()
                .map(value -> "\"" + value + "\"")
                .reduce((a, b) -> a + "," + b)
                .map(text -> "[" + text + "]")
                .orElse("[]");
    }

    private void audit(
            Long actorUserId,
            long subjectUserId,
            String eventType,
            String contextJson) {

        jdbcTemplate.update(
                """
                INSERT INTO auth_audit_event
                    (event_uid, actor_user_id, subject_user_id, session_uid, event_type,
                     outcome, reason_code, identifier_hmac, ip_prefix, user_agent_label,
                     trace_id, context_json)
                VALUES
                    (?, ?, ?, NULL, ?, 'SUCCESS', NULL, NULL, 'local', 'admin', ?, ?)
                """,
                IdentityIds.eventUid(),
                actorUserId,
                subjectUserId,
                eventType,
                "admin-" + IdentityIds.randomToken(12),
                contextJson);
    }
}
