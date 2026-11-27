package com.vedioai.server.identity.web;

public record RequestMetadata(String ipPrefix, String userAgentLabel, String traceId) {
}