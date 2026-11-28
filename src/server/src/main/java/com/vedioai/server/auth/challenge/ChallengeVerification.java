package com.vedioai.server.auth.challenge;

public record ChallengeVerification(
        long challengeId,
        String challengeUid,
        String purpose,
        String emailNorm,
        long channelId,
        long generation,
        long version,
        Long subjectUserId,
        Long issuedAuthVersion) {
}