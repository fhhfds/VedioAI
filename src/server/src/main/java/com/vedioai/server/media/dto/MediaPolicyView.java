package com.vedioai.server.media.dto;

import java.util.List;

public record MediaPolicyView(
        String policyVersion,
        long maxFileBytes,
        long maxVideoDurationMs,
        int chunkSizeBytes,
        int maxConcurrentParts,
        int maxActiveUploads,
        long sessionTtlSeconds,
        List<String> allowedExtensions,
        List<String> allowedVideoCodecs,
        List<String> allowedAudioCodecs,
        long playbackTicketTtlSeconds) {
}