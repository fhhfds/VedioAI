package com.vedioai.server.common.web;

public record RequestMetadata(String ipPrefix, String userAgentLabel, String traceId) {
}