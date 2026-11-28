package com.vedioai.server.media.dto;

import java.util.List;

public record MediaDetailView(
        String mediaId,
        String displayName,
        long sizeBytes,
        Long durationMs,
        String status,
        long version,
        String createdAt,
        List<String> allowedActions,
        String originalName,
        String container,
        String videoCodec,
        String audioCodec,
        Integer width,
        Integer height,
        MediaFailureView failure,
        String processingJobId,
        DeletionImpactView deletion) {
}