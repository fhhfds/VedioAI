package com.vedioai.server.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vedioai.server.auth.challenge.ChallengeReceipt;
import com.vedioai.server.auth.challenge.RegistrationChallengeService;
import com.vedioai.server.auth.mail.MailPayloadCipher;
import com.vedioai.server.auth.service.RegistrationService;
import com.vedioai.server.common.web.RequestMetadata;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;


@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "vedioai.auth.mail.delivery-mode=local-catcher",
                "vedioai.auth.mail.dispatcher-enabled=false"
        })
class LoginFlowIT {

    private static final Pattern JSON_STRING = Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]+)\"");
    private static final String SESSION_COOKIE = "VedioAI-Session-Dev=";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private RegistrationChallengeService challengeService;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private MailPayloadCipher mailPayloadCipher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    void loginMeRefreshLogoutAndOldSessionInvalidation() throws Exception {
        String email = "login+" + UUID.randomUUID().toString().replace("-", "") + "@example.invalid";
        String password = "LoginFlow-Passphrase42!";
        registerUser(email, password);

        HttpResponse<String> csrfResponse = request("GET", "/api/auth/csrf", null, null, null);
        assertEquals(200, csrfResponse.statusCode());
        String csrfCookie = sessionCookie(csrfResponse);
        String csrfToken = jsonString(csrfResponse.body(), "token");
        assertNotNull(csrfToken);

        String loginBody = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        HttpResponse<String> login = request(
                "POST", "/api/auth/login", loginBody, csrfCookie, csrfToken);
        assertEquals(200, login.statusCode(), login.body());
        String loginCookie = sessionCookie(login);
        String userId = jsonString(login.body(), "userId");
        assertNotNull(userId);
        assertTrue(userId.startsWith("usr_"));
        assertTrue(login.body().contains("\"sessionId\":\"ses_"));

        HttpResponse<String> me = request("GET", "/api/auth/me", null, loginCookie, null);
        assertEquals(200, me.statusCode(), me.body());
        assertTrue(me.body().contains("\"userId\":\"" + userId + "\""));
        assertTrue(me.body().contains("\"sessionId\":\"ses_"));

        Long databaseUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM auth_user WHERE email_norm = ?",
                Long.class,
                email);
        assertNotNull(databaseUserId);
        String activeStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM auth_session_guard WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                databaseUserId);
        assertEquals("ACTIVE", activeStatus);

        HttpResponse<String> refreshed = request("GET", "/api/auth/me", null, loginCookie, null);
        assertEquals(200, refreshed.statusCode(), refreshed.body());

        HttpResponse<String> nextCsrf = request("GET", "/api/auth/csrf", null, loginCookie, null);
        assertEquals(200, nextCsrf.statusCode());
        String nextToken = jsonString(nextCsrf.body(), "token");

        HttpResponse<String> logout = request(
                "POST", "/api/auth/logout", null, loginCookie, nextToken);
        assertEquals(204, logout.statusCode(), logout.body());

        String revokedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM auth_session_guard WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                databaseUserId);
        assertEquals("REVOKED", revokedStatus);

        HttpResponse<String> afterLogout = request("GET", "/api/auth/me", null, loginCookie, null);
        assertEquals(401, afterLogout.statusCode());

        HttpResponse<String> freshCsrf = request("GET", "/api/auth/csrf", null, null, null);
        String freshCookie = sessionCookie(freshCsrf);
        String freshToken = jsonString(freshCsrf.body(), "token");
        String wrongBody = "{\"email\":\"" + email + "\",\"password\":\"wrong password long\"}";
        HttpResponse<String> wrong = request(
                "POST", "/api/auth/login", wrongBody, freshCookie, freshToken);
        assertEquals(401, wrong.statusCode(), wrong.body());
    }

    private void registerUser(String email, String password) {
        String binding = "binding-" + UUID.randomUUID();
        ChallengeReceipt receipt = challengeService.request(email, binding);
        Long challengeId = jdbcTemplate.queryForObject(
                "SELECT id FROM auth_verification_challenge WHERE challenge_uid = ?",
                Long.class,
                receipt.challengeId());
        assertNotNull(challengeId);

        Map<String, Object> outbox = jdbcTemplate.queryForMap(
                """
                SELECT message_key, payload_cipher, cipher_nonce, cipher_key_id
                FROM auth_mail_outbox WHERE challenge_id = ?
                """,
                challengeId);
        String code = mailPayloadCipher.decrypt(
                (String) outbox.get("message_key"),
                (String) outbox.get("cipher_key_id"),
                (byte[]) outbox.get("cipher_nonce"),
                (byte[]) outbox.get("payload_cipher"));

        registrationService.register(
                receipt.challengeId(),
                code,
                "登录测试用户",
                password,
                binding,
                new RequestMetadata("127.0.0.1", "LoginFlowIT", "trace-" + UUID.randomUUID()));
    }

    private String sessionCookie(HttpResponse<String> response) {
        return response.headers().allValues("set-cookie").stream()
                .filter(value -> value.startsWith(SESSION_COOKIE))
                .reduce((first, second) -> second)
                .map(value -> value.split(";", 2)[0])
                .orElseThrow(() -> new AssertionError("session cookie missing: "
                        + response.headers().allValues("set-cookie")));
    }

    private HttpResponse<String> request(
            String method,
            String path,
            String body,
            String cookie,
            String csrf) throws Exception {

        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10));

        if ("POST".equals(method)) {
            builder.POST(body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.GET();
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        if (cookie != null) {
            builder.header("Cookie", cookie);
        }
        if (csrf != null) {
            builder.header("X-CSRF-TOKEN", csrf);
        }

        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String jsonString(String body, String key) {
        Matcher matcher = Pattern.compile(String.format(JSON_STRING.pattern(), key))
                .matcher(body == null ? "" : body);
        return matcher.find() ? matcher.group(1) : null;
    }
}
