package com.vedioai.server.auth.exception;

public class LastAdminException extends RuntimeException {

    public LastAdminException() {
        super("必须保留至少一个可用的管理员");
    }
}