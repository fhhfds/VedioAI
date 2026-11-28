package com.vedioai.server.auth.challenge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vedioai.server.auth.mail.MailOutboxDispatcher;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;


@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "vedioai.auth.mail.delivery-mode=local-catcher",
                "vedioai.auth.mail.dispatcher-enabled=false"
        })
class RegistrationChallengeMailIT {

    private static final Pattern MESSAGE_ID = Pattern.compile("\"ID\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern TEXT_FIELD = Pattern.compile(
            "\"Text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern SIX_DIGITS = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)");

    @Autowired
    private RegistrationChallengeService challengeService;

    @Autowired
    private MailOutboxDispatcher dispatcher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    void createsEncryptedOutboxAndDeliversEightDigitCodeToMailpit() throws Exception {
        String email = "creator+" + UUID.randomUUID().toString().replace("-", "") + "@example.invalid";
        String browserBinding = "binding-" + UUID.randomUUID();

        ChallengeReceipt receipt = challengeService.request(email, browserBinding);
        assertNotNull(receipt.challengeId());
        assertTrue(receipt.challengeId().startsWith("chl_"));
        assertEquals(600, receipt.expiresIn());
        assertEquals(60, receipt.resendAfter());

        Long challengeId = jdbcTemplate.queryForObject(
                "SELECT id FROM auth_verification_challenge WHERE challenge_uid = ?",
                Long.class,
                receipt.challengeId());
        assertNotNull(challengeId);

        byte[] otpMac = jdbcTemplate.queryForObject(
                "SELECT otp_mac FROM auth_verification_challenge WHERE id = ?",
                byte[].class,
                challengeId);
        assertEquals(32, otpMac.length);

        Map<String, Object> before = jdbcTemplate.queryForMap(
                """
                SELECT status, payload_cipher, cipher_nonce, cipher_key_id
                FROM auth_mail_outbox WHERE challenge_id = ?
                """,
                challengeId);
        assertEquals("PENDING", before.get("status"));
        byte[] encryptedPayload = (byte[]) before.get("payload_cipher");
        byte[] nonce = (byte[]) before.get("cipher_nonce");
        assertNotNull(encryptedPayload);
        assertNotNull(nonce);
        assertEquals(12, nonce.length);
        assertNotNull(before.get("cipher_key_id"));

        jdbcTemplate.update(
                """
                UPDATE auth_mail_outbox
                SET next_run_at = DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 1 DAY)
                WHERE challenge_id <> ? AND status IN ('PENDING', 'SENDING')
                """,
                challengeId);

        assertTrue(dispatcher.dispatchOnce());

        Map<String, Object> after = jdbcTemplate.queryForMap(
                """
                SELECT status, payload_cipher, cipher_nonce, sent_at
                FROM auth_mail_outbox WHERE challenge_id = ?
                """,
                challengeId);
        assertEquals("SENT", after.get("status"));
        assertEquals(null, after.get("payload_cipher"));
        assertNotNull(after.get("sent_at"));

        String code = awaitMailpitCode(email);
        assertEquals(6, code.length());
        assertFalse(contains(encryptedPayload, code.getBytes(StandardCharsets.UTF_8)));
        assertFalse(contains(otpMac, code.getBytes(StandardCharsets.UTF_8)));
    }

    private String awaitMailpitCode(String recipient) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            String list = get("http://127.0.0.1:8025/api/v1/messages");
            Matcher ids = MESSAGE_ID.matcher(list);
            while (ids.find()) {
                String detail = get("http://127.0.0.1:8025/api/v1/message/" + ids.group(1));
                if (!detail.contains(recipient)) {
                    continue;
                }
                Matcher text = TEXT_FIELD.matcher(detail);
                if (!text.find()) {
                    continue;
                }
                Matcher code = SIX_DIGITS.matcher(text.group(1));
                if (code.find()) {
                    return code.group(1);
                }
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Mailpit did not receive a 6-digit code for " + recipient);
    }

    private String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        return response.body();
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        if (haystack == null || needle.length == 0 || haystack.length < needle.length) {
            return false;
        }
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
