package com.vedioai.server.auth.exception;

public class SelfModificationException extends RuntimeException {

    public SelfModificationException() {
        super("不能通过管理接口修改当前登录账号");
    }
}