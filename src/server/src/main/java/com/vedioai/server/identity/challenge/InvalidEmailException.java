package com.vedioai.server.identity.challenge;

public class InvalidEmailException extends RuntimeException {

    public InvalidEmailException() {
        super("邮箱格式不正确");
    }
}