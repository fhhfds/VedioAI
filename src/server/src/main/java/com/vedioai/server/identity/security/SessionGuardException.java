package com.vedioai.server.identity.security;

public class SessionGuardException extends RuntimeException {

    public SessionGuardException(String message) {
        super(message);
    }
}