package com.vedioai.server.auth.domain;

import java.util.List;


public record AuthenticatedUser(
        long userId,
        String userUid,
        String email,
        String nickname,
        String status,
        long authVersion,
        long version,
        String createdAt,
        boolean emailVerified,
        List<String> roles,
        String passwordHash,
        long credentialVersion) {
}
