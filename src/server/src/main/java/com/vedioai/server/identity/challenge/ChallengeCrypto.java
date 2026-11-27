package com.vedioai.server.identity.challenge;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.vedioai.server.identity.config.VedioaiAuthProperties;

import org.springframework.stereotype.Component;

@Component
public class ChallengeCrypto {

    private final byte[] hmacKey;
    private final String hmacKeyId;
    private final byte[] bindingKey;
    private final String bindingKeyId;

    public ChallengeCrypto(VedioaiAuthProperties properties) {
        VedioaiAuthProperties.Challenge challenge = properties.getChallenge();
        this.hmacKey = decode32(challenge.getHmacKeyBase64(), "challenge.hmac-key-base64");
        this.hmacKeyId = challenge.getHmacKeyId();
        this.bindingKey = decode32(challenge.getBindingKeyBase64(), "challenge.binding-key-base64");
        this.bindingKeyId = challenge.getBindingKeyId();
    }

    public byte[] hmacChallenge(String purpose, String challengeUid, String emailNorm, String code) {
        return hmac(hmacKey, purpose + "\u0000" + challengeUid + "\u0000" + emailNorm + "\u0000" + code);
    }

    public byte[] hmacBinding(String browserBinding) {
        return hmac(bindingKey, browserBinding);
    }

    public String getHmacKeyId() {
        return hmacKeyId;
    }

    public String getBindingKeyId() {
        return bindingKeyId;
    }

    private static byte[] hmac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", exception);
        }
    }

    private static byte[] decode32(String base64, String name) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(name + " is not valid Base64", exception);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException(name + " must decode to 32 bytes");
        }
        return decoded;
    }
}