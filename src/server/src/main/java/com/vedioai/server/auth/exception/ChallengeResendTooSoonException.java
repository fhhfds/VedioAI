package com.vedioai.server.auth.exception;

public class ChallengeResendTooSoonException extends RuntimeException {

    private final long retryAfterSeconds;

    public ChallengeResendTooSoonException(long retryAfterSeconds) {
        super("验证码发送过于频繁，请稍后再试");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}