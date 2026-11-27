package com.vedioai.server.identity.security;

public class PasswordCapacityExceededException extends RuntimeException {

    public PasswordCapacityExceededException() {
        super("password computation capacity exhausted");
    }
}