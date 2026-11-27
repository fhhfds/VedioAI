package com.vedioai.server.identity.challenge;

import java.util.Locale;
import java.util.regex.Pattern;

public final class EmailNormalizer {

    private static final Pattern EMAIL = Pattern.compile(
            "^[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"
                    + "(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$");

    private EmailNormalizer() {
    }

    public static String normalize(String input) {
        if (input == null) {
            throw new InvalidEmailException();
        }
        String normalized = input.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 254 || !EMAIL.matcher(normalized).matches()) {
            throw new InvalidEmailException();
        }
        return normalized;
    }
}