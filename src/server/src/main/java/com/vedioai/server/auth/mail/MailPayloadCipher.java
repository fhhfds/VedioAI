package com.vedioai.server.auth.mail;

import com.vedioai.server.auth.config.VedioaiAuthProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;


@Component
public class MailPayloadCipher {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final byte[] key;
    private final String keyId;
    private final SecureRandom random = new SecureRandom();

    public MailPayloadCipher(VedioaiAuthProperties properties) {
        VedioaiAuthProperties.Mail mail = properties.getMail();
        this.keyId = mail.getPayloadKeyId();
        this.key = decode32(mail.getPayloadKeyBase64());
    }

    public EncryptedPayload encrypt(String messageKey, String plaintext) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(messageKey.getBytes(StandardCharsets.UTF_8));
            return new EncryptedPayload(nonce, cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("mail payload encryption failed", exception);
        }
    }

    public String decrypt(String messageKey, String keyId, byte[] nonce, byte[] ciphertext) {
        if (!this.keyId.equals(keyId)) {
            throw new IllegalStateException("unknown mail payload key id");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(messageKey.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("mail payload decryption failed", exception);
        }
    }

    public String getKeyId() {
        return keyId;
    }

    private static byte[] decode32(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException("vedioai.auth.mail.payload-key-base64 is required");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("mail payload key is not valid Base64", exception);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException("mail payload key must decode to 32 bytes");
        }
        return decoded;
    }

    public record EncryptedPayload(byte[] nonce, byte[] cipher) {
    }
}
