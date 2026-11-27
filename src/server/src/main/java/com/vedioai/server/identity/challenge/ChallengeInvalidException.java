package com.vedioai.server.identity.challenge;

public class ChallengeInvalidException extends RuntimeException {

    public ChallengeInvalidException() {
        super("验证码无效或已过期");
    }
}