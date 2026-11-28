package com.vedioai.server.auth.exception;

public class SessionExpiredException extends RuntimeException {

    public SessionExpiredException() {
        super("登录已失效，请重新登录");
    }
}