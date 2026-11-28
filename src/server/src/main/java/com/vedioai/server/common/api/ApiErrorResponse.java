package com.vedioai.server.common.api;

public record ApiErrorResponse(ErrorBody error, String traceId) {

    public record ErrorBody(String code, String message, Object details) {
    }
}