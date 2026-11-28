package com.vedioai.server.auth.exception;

public class SessionLimitException extends RuntimeException {

    public SessionLimitException() {
        super("有效会话数量已达上限，请先撤销其他设备");
    }
}