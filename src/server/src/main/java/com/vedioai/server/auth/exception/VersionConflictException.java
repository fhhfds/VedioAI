package com.vedioai.server.auth.exception;

public class VersionConflictException extends RuntimeException {

    public VersionConflictException() {
        super("数据已被其他操作修改，请刷新后重试");
    }
}