package com.vedioai.server.identity.challenge;

public record ChallengeReceipt(String challengeId, long expiresIn, long resendAfter) {
}