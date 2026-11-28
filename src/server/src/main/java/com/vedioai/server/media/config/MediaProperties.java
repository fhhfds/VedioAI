package com.vedioai.server.media.config;

import java.time.Duration;
import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "vedioai.media")
public class MediaProperties {

    @NotBlank
    private String policyVersion = "media-policy-v1";

    @Min(1)
    private long maxFileBytes = 2L * 1024 * 1024 * 1024;

    @Min(1)
    private long maxVideoDurationMs = 7_200_000L;

    @Min(1)
    private int chunkSizeBytes = 5 * 1024 * 1024;

    @Min(1)
    private int maxConcurrentParts = 3;

    @Min(1)
    private int maxActiveUploads = 2;

    @NotNull
    private Duration sessionTtl = Duration.ofHours(24);

    @NotNull
    private Duration playbackTicketTtl = Duration.ofMinutes(5);

    @NotEmpty
    private List<String> allowedExtensions = List.of("mp4");

    @NotEmpty
    private List<String> allowedVideoCodecs = List.of("h264");

    @NotEmpty
    private List<String> allowedAudioCodecs = List.of("aac");

    @NotBlank
    private String storageRoot = "./runtime/media-store";

    public String getPolicyVersion() {
        return policyVersion;
    }

    public void setPolicyVersion(String policyVersion) {
        this.policyVersion = policyVersion;
    }

    public long getMaxFileBytes() {
        return maxFileBytes;
    }

    public void setMaxFileBytes(long maxFileBytes) {
        this.maxFileBytes = maxFileBytes;
    }

    public long getMaxVideoDurationMs() {
        return maxVideoDurationMs;
    }

    public void setMaxVideoDurationMs(long maxVideoDurationMs) {
        this.maxVideoDurationMs = maxVideoDurationMs;
    }

    public int getChunkSizeBytes() {
        return chunkSizeBytes;
    }

    public void setChunkSizeBytes(int chunkSizeBytes) {
        this.chunkSizeBytes = chunkSizeBytes;
    }

    public int getMaxConcurrentParts() {
        return maxConcurrentParts;
    }

    public void setMaxConcurrentParts(int maxConcurrentParts) {
        this.maxConcurrentParts = maxConcurrentParts;
    }

    public int getMaxActiveUploads() {
        return maxActiveUploads;
    }

    public void setMaxActiveUploads(int maxActiveUploads) {
        this.maxActiveUploads = maxActiveUploads;
    }

    public Duration getSessionTtl() {
        return sessionTtl;
    }

    public void setSessionTtl(Duration sessionTtl) {
        this.sessionTtl = sessionTtl;
    }

    public Duration getPlaybackTicketTtl() {
        return playbackTicketTtl;
    }

    public void setPlaybackTicketTtl(Duration playbackTicketTtl) {
        this.playbackTicketTtl = playbackTicketTtl;
    }

    public List<String> getAllowedExtensions() {
        return allowedExtensions;
    }

    public void setAllowedExtensions(List<String> allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }

    public List<String> getAllowedVideoCodecs() {
        return allowedVideoCodecs;
    }

    public void setAllowedVideoCodecs(List<String> allowedVideoCodecs) {
        this.allowedVideoCodecs = allowedVideoCodecs;
    }

    public List<String> getAllowedAudioCodecs() {
        return allowedAudioCodecs;
    }

    public void setAllowedAudioCodecs(List<String> allowedAudioCodecs) {
        this.allowedAudioCodecs = allowedAudioCodecs;
    }

    public String getStorageRoot() {
        return storageRoot;
    }

    public void setStorageRoot(String storageRoot) {
        this.storageRoot = storageRoot;
    }
}