package com.vedioai.server.identity.challenge;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("该邮箱已注册，请直接登录");
    }
}