package com.vedioai.server.auth.security;

import jakarta.servlet.http.HttpSession;
import java.util.UUID;


public final class BrowserBindings {

    private static final String ATTRIBUTE = BrowserBindings.class.getName() + ".binding";

    private BrowserBindings() {
    }

    public static String getOrCreate(HttpSession session) {
        Object existing = session.getAttribute(ATTRIBUTE);
        if (existing instanceof String binding && !binding.isBlank()) {
            return binding;
        }
        String binding = UUID.randomUUID().toString();
        session.setAttribute(ATTRIBUTE, binding);
        return binding;
    }
}
