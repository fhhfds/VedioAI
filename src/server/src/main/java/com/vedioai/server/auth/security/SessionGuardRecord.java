package com.vedioai.server.auth.security;

import java.time.Instant;


public record SessionGuardRecord(
        long id,
        String sessionUid,
        long userId,
        long authVersion,
        String status,
        Instant createdAt,
        Instant authenticatedAt,
        Instant absoluteExpiresAt,
        Instant lastSeenAt,
        String userAgent,
        String ipLabel) {
}
