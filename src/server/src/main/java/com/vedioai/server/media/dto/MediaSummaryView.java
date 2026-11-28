package com.vedioai.server.media.dto;

import java.util.List;

public record MediaSummaryView(
        String mediaId,
        String displayName,
        long sizeBytes,
        Long durationMs,
        String status,
        long version,
        String createdAt,
        List<String> allowedActions) {
}