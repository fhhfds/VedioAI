package com.vedioai.server.auth.exception;

public class AccountDisabledException extends RuntimeException {

    public AccountDisabledException() {
        super("账号已被停用");
    }
}