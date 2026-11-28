package com.vedioai.server.auth.exception;

public class CurrentPasswordInvalidException extends RuntimeException {

    public CurrentPasswordInvalidException() {
        super("当前密码不正确");
    }
}