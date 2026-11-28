package com.vedioai.server.auth.security;

public record SessionGuardView(
        String sessionUid,
        String authenticatedAt,
        String absoluteExpiresAt,
        long authVersion) {
}