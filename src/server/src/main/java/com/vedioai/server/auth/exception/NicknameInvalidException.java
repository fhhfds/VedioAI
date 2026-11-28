package com.vedioai.server.auth.exception;

public class NicknameInvalidException extends RuntimeException {

    public NicknameInvalidException() {
        super("昵称长度必须为 1 到 64 个 Unicode 码点");
    }
}