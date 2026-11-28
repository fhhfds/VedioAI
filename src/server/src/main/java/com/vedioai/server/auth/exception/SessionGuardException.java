package com.vedioai.server.auth.exception;

public class SessionGuardException extends RuntimeException {

    public SessionGuardException(String message) {
        super(message);
    }
}