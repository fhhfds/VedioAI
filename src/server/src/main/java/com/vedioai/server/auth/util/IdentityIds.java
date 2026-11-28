package com.vedioai.server.auth.util;

import java.security.SecureRandom;
import java.util.Base64;


public final class IdentityIds {

    private static final SecureRandom RANDOM = new SecureRandom();

    private IdentityIds() {
    }

    public static String randomToken(int byteCount) {
        byte[] bytes = new byte[byteCount];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String eventUid() {
        return "evt_" + randomToken(18);
    }
}
