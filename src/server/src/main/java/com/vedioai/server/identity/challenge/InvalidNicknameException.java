package com.vedioai.server.identity.challenge;

public class InvalidNicknameException extends RuntimeException {

    public InvalidNicknameException() {
        super("昵称需为 1 到 64 个字符");
    }
}