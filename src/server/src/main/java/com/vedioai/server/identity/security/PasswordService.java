package com.vedioai.server.identity.security;

import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

import org.springframework.security.crypto.password.PasswordEncoder;

public class PasswordService {

    private static final int COMPUTATION_SLOTS = 2;

    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final Semaphore slots = new Semaphore(COMPUTATION_SLOTS, true);

    public PasswordService(PasswordEncoder encoder, PasswordPolicy policy) {
        this.encoder = encoder;
        this.policy = policy;
    }

    public String encode(String rawPassword) {
        String normalized = policy.validate(rawPassword);
        return withSlot(() -> encoder.encode(normalized));
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        String normalized = policy.normalize(rawPassword);
        return withSlot(() -> encoder.matches(normalized, encodedPassword));
    }

    public boolean needsUpgrade(String encodedPassword) {
        return encodedPassword != null && encoder.upgradeEncoding(encodedPassword);
    }

    private <T> T withSlot(Supplier<T> action) {
        if (!slots.tryAcquire()) {
            throw new PasswordCapacityExceededException();
        }
        try {
            return action.get();
        } finally {
            slots.release();
        }
    }
}