package com.vedioai.server.auth.dto;

public record AuthStateResponse(AuthUserView user, SessionView session) {
}