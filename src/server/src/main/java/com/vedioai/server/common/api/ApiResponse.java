package com.vedioai.server.common.api;

public record ApiResponse<T>(T data, String traceId) {
}