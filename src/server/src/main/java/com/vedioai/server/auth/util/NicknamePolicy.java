package com.vedioai.server.auth.util;

import com.vedioai.server.auth.exception.NicknameInvalidException;
import java.text.Normalizer;


public final class NicknamePolicy {

    public static final int MAX_CODE_POINTS = 64;

    private NicknamePolicy() {
    }

    public static String normalize(String nickname) {
        String normalized = nickname == null
                ? ""
                : Normalizer.normalize(nickname.trim(), Normalizer.Form.NFC);
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints < 1 || codePoints > MAX_CODE_POINTS) {
            throw new NicknameInvalidException();
        }
        return normalized;
    }
}
