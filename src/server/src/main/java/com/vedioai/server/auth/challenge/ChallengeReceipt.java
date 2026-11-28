package com.vedioai.server.auth.challenge;

public record ChallengeReceipt(String challengeId, long expiresIn, long resendAfter) {
}