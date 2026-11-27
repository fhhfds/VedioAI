package com.vedioai.server.identity.security;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

public class PasswordPolicy {

    public static final int MIN_CODE_POINTS = 8;
    public static final int MAX_CODE_POINTS = 128;

    private static final Set<String> BANNED_FRAGMENTS = Set.of(
            "password",
            "123456",
            "qwerty",
            "letmein",
            "admin",
            "vedioai",
            "welcome",
            "iloveyou",
            "abc123",
            "111111",
            "000000",
            "passw0rd");

    public String validate(String rawPassword) {
        if (rawPassword == null) {
            throw new WeakPasswordException("密码不能为空");
        }

        String normalized = normalize(rawPassword);
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints < MIN_CODE_POINTS || codePoints > MAX_CODE_POINTS) {
            throw new WeakPasswordException("密码长度必须为 15 到 128 个 Unicode 码点");
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        for (String banned : BANNED_FRAGMENTS) {
            if (lower.contains(banned)) {
                throw new WeakPasswordException("密码包含常见弱口令片段");
            }
        }

        return normalized;
    }

    public String normalize(String rawPassword) {
        return rawPassword == null ? null : Normalizer.normalize(rawPassword, Normalizer.Form.NFC);
    }
}