package com.vedioai.server.auth.exception;

public class RegistrationDisabledException extends RuntimeException {

    public RegistrationDisabledException() {
        super("注册暂未开放");
    }
}