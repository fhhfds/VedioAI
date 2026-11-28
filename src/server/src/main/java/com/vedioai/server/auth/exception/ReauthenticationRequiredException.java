package com.vedioai.server.auth.exception;

public class ReauthenticationRequiredException extends RuntimeException {

    public ReauthenticationRequiredException() {
        super("该操作需要重新验证当前密码");
    }
}