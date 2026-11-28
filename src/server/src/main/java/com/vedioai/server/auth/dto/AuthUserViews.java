package com.vedioai.server.auth.dto;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;


public final class AuthUserViews {

    public static final List<String> USER_PERMISSIONS = List.of(
            "account:self:read",
            "account:self:write",
            "session:self:manage",
            "media:own:read",
            "media:own:write",
            "media:own:play",
            "media:own:delete",
            "job:own:read");

    private static final List<String> ADMIN_PERMISSIONS = List.of(
            "admin:users:read",
            "admin:users:write");

    private AuthUserViews() {
    }

    public static List<String> permissionsFor(List<String> roles) {
        if (roles.contains("ADMIN")) {
            return java.util.stream.Stream.concat(
                            USER_PERMISSIONS.stream(),
                            ADMIN_PERMISSIONS.stream())
                    .toList();
        }
        return USER_PERMISSIONS;
    }

    public static AuthUserView of(
            String userUid,
            String email,
            String nickname,
            String status,
            boolean emailVerified,
            List<String> roles,
            long version,
            String createdAt) {

        return new AuthUserView(
                userUid,
                email,
                nickname,
                status,
                emailVerified,
                List.copyOf(roles),
                permissionsFor(roles),
                version,
                createdAt);
    }

    public static String instantString(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().toString();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toInstant(ZoneOffset.UTC).toString();
        }
        return value == null ? Instant.EPOCH.toString() : value.toString();
    }
}
