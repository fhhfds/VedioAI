package com.vedioai.server.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MapPropertySource;


class VedioaiAuthPropertiesTest {

    private static final String KEY_32 = Base64.getEncoder().encodeToString(new byte[32]);

    private static final Map<String, Object> REQUIRED = Map.of(
            "vedioai.auth.challenge.hmac-key-id", "test-otp",
            "vedioai.auth.challenge.hmac-key-base64", KEY_32,
            "vedioai.auth.challenge.binding-key-id", "test-binding",
            "vedioai.auth.challenge.binding-key-base64", KEY_32,
            "vedioai.auth.mail.from", "no-reply@test.invalid",
            "vedioai.auth.mail.payload-key-id", "test-mail",
            "vedioai.auth.mail.payload-key-base64", KEY_32);

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void bindsDefaultsAndKeepsUnits() {
        VedioaiAuthProperties properties = bind(REQUIRED);

        assertEquals("VedioAI-Session-Dev", properties.getCookie().getName());
        assertEquals(false, properties.getCookie().isSecure());
        assertEquals(true, properties.getCookie().isHttpOnly());
        assertEquals("Lax", properties.getCookie().getSameSite());
        assertEquals(Duration.ofHours(12), properties.getSession().getAbsoluteTtl());
        assertEquals(5, properties.getSession().getMaxActive());
        assertEquals(Duration.ofMinutes(5), properties.getReauthentication().getTtl());
        assertEquals(65536, properties.getPassword().getArgon2().getMemoryKib());
        assertEquals(3, properties.getPassword().getArgon2().getIterations());
        assertEquals(1, properties.getPassword().getArgon2().getParallelism());
        assertEquals(6, properties.getChallenge().getOtpLength());
        assertEquals(Duration.ofMinutes(10), properties.getChallenge().getTtl());
        assertEquals(5, properties.getChallenge().getMaxAttempts());
        assertEquals(Duration.ofSeconds(60), properties.getChallenge().getResendAfter());
        assertEquals("no-reply@test.invalid", properties.getMail().getFrom());
        assertEquals("local-catcher", properties.getMail().getDeliveryMode());
        assertTrue(properties.getMail().isDispatcherEnabled());
        assertFalse(properties.isRegistrationEnabled());
        assertTrue(validator.validate(properties).isEmpty());
    }

    @Test
    void rejectsArgon2MemoryBelowMinimum() {
        VedioaiAuthProperties properties = bind(with(REQUIRED,
                "vedioai.auth.password.argon2.memory-kib", "1024"));

        Set<ConstraintViolation<VedioaiAuthProperties>> violations = validator.validate(properties);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(violation -> violation.getPropertyPath().toString()
                        .contains("password.argon2.memoryKib")));
    }

    @Test
    void rejectsEmptyCookieName() {
        VedioaiAuthProperties properties = bind(with(REQUIRED,
                "vedioai.auth.cookie.name", ""));

        Set<ConstraintViolation<VedioaiAuthProperties>> violations = validator.validate(properties);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(violation -> violation.getPropertyPath().toString()
                        .contains("cookie.name")));
    }

    private Map<String, Object> with(Map<String, Object> base, String key, Object value) {
        Map<String, Object> values = new HashMap<>(base);
        values.put(key, value);
        return values;
    }

    private VedioaiAuthProperties bind(Map<String, Object> values) {
        MapPropertySource source = new MapPropertySource("test", values);
        Binder binder = new Binder(ConfigurationPropertySources.from(source));
        return binder.bind("vedioai.auth", Bindable.of(VedioaiAuthProperties.class))
                .orElseGet(VedioaiAuthProperties::new);
    }
}
