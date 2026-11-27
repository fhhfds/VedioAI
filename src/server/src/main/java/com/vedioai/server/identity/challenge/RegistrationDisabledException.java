package com.vedioai.server.identity.challenge;

public class RegistrationDisabledException extends RuntimeException {

    public RegistrationDisabledException() {
        super("注册暂未开放");
    }
}