package com.vedioai.server.media.service;

import java.util.List;

import com.vedioai.server.media.config.MediaProperties;
import com.vedioai.server.media.dto.MediaPolicyView;

import org.springframework.stereotype.Service;

@Service
public class MediaPolicyService {

    private final MediaProperties properties;

    public MediaPolicyService(MediaProperties properties) {
        this.properties = properties;
    }

    public MediaPolicyView policy() {
        return new MediaPolicyView(
                properties.getPolicyVersion(),
                properties.getMaxFileBytes(),
                properties.getMaxVideoDurationMs(),
                properties.getChunkSizeBytes(),
                properties.getMaxConcurrentParts(),
                properties.getMaxActiveUploads(),
                properties.getSessionTtl().toSeconds(),
                List.copyOf(properties.getAllowedExtensions()),
                List.copyOf(properties.getAllowedVideoCodecs()),
                List.copyOf(properties.getAllowedAudioCodecs()),
                properties.getPlaybackTicketTtl().toSeconds());
    }
}