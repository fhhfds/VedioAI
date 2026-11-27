package com.vedioai.server.identity.challenge;

public record ChallengeVerification(
        long challengeId,
        String challengeUid,
        String emailNorm,
        long channelId,
        long generation,
        long version) {
}