package com.vedioai.server.identity.web;

public record ApiErrorResponse(ErrorBody error, String traceId) {

    public record ErrorBody(String code, String message, Object details) {
    }
}