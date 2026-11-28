package com.vedioai.server.auth.dto;

import java.util.List;


public record AuthUserView(
        String userId,
        String email,
        String nickname,
        String status,
        boolean emailVerified,
        List<String> roles,
        List<String> permissions,
        long version,
        String createdAt) {
}
