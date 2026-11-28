package com.vedioai.server.media.domain;

public enum MediaStatus {
    PROBE_PENDING,
    READY,
    FAILED,
    DELETED;

    public boolean isPlayable() {
        return this == READY;
    }

    public boolean isDeletable() {
        return this == READY || this == FAILED;
    }
}