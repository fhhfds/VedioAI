package com.vedioai.server.auth.exception;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("该邮箱已注册，请直接登录");
    }
}