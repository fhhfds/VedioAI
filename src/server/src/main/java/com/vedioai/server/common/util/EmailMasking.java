package com.vedioai.server.common.util;

public final class EmailMasking {

    private EmailMasking() {
    }

    public static String mask(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() == 1) {
            return "*" + domain;
        }
        return local.charAt(0) + "***" + domain;
    }
}