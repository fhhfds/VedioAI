package com.vedioai.server.auth.exception;

public class AlreadyAuthenticatedException extends RuntimeException {

    public AlreadyAuthenticatedException() {
        super("当前会话已经登录");
    }
}