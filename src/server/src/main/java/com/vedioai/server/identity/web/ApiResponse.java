package com.vedioai.server.identity.web;

public record ApiResponse<T>(T data, String traceId) {
}