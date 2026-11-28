package com.vedioai.server.auth.exception;

public class PasswordCapacityExceededException extends RuntimeException {

    public PasswordCapacityExceededException() {
        super("password computation capacity exhausted");
    }
}