package com.vedioai.server.auth.dto;

public record SessionView(
        String sessionId,
        String authenticatedAt,
        String absoluteExpiresAt) {
}