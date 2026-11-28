package com.vedioai.server.auth.dto;

public record SessionItemView(
        String sessionId,
        boolean current,
        String createdAt,
        String lastSeenAt,
        String expiresAt,
        String userAgent,
        String ipLabel) {
}