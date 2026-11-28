package com.vedioai.server.auth.dto;

import java.util.List;


public record AdminUserView(
        String userId,
        String emailLabel,
        String nickname,
        String status,
        List<String> roles,
        long version,
        String createdAt) {
}
