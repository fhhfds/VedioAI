package com.vedioai.server.identity.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.password4j.Argon2Function;
import com.password4j.types.Argon2;
import com.vedioai.server.identity.config.VedioaiAuthProperties;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password4j.Argon2Password4jPasswordEncoder;

class PasswordServiceTest {

    private final VedioaiAuthProperties properties = new VedioaiAuthProperties();

    @Test
    void encodesAndVerifiesNormalizedPassword() {
        PasswordService service = passwordService();
        String raw = "correct horse battery staple";

        String encoded = service.encode(raw);

        assertTrue(encoded.contains("$argon2id$"));
        assertTrue(service.matches(raw, encoded));
        assertFalse(service.matches("another long password phrase", encoded));
        assertFalse(service.needsUpgrade(encoded));
    }

    @Test
    void rejectsShortPassword() {
        PasswordService service = passwordService();

        assertThrows(WeakPasswordException.class, () -> service.encode("short"));
    }

    @Test
    void rejectsBannedFragment() {
        PasswordService service = passwordService();

        assertThrows(WeakPasswordException.class,
                () -> service.encode("this password is long but banned"));
    }

    private PasswordService passwordService() {
        VedioaiAuthProperties.Password.Argon2 argon2 = properties.getPassword().getArgon2();
        Argon2Function function = Argon2Function.getInstance(
                argon2.getMemoryKib(),
                argon2.getIterations(),
                argon2.getParallelism(),
                32,
                Argon2.ID);
        return new PasswordService(
                new Argon2Password4jPasswordEncoder(function),
                new PasswordPolicy());
    }
}