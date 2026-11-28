package com.vedioai.server.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vedioai.server.auth.mail.MailOutboxDispatcher;
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
class RegistrationFlowIT {

    private static final Pattern JSON_STRING = Pattern.compile("\"%s\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern MESSAGE_ID = Pattern.compile("\"ID\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern TEXT_FIELD = Pattern.compile(
            "\"Text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern SIX_DIGITS = Pattern.compile("(?<!\\d)(\\d{6})(?!\\d)");

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private MailOutboxDispatcher dispatcher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    void registersOnceAndDoesNotAutoLogin() throws Exception {
        String email = "register+" + UUID.randomUUID().toString().replace("-", "") + "@example.invalid";
        String password = "Cloud rivers cross quiet meadows 42!";

        HttpResponse<String> csrf = request("GET", "/api/auth/csrf", null, null, null);
        assertEquals(200, csrf.statusCode());
        String sessionCookie = csrf.headers().allValues("set-cookie").stream()
                .filter(value -> value.startsWith("VedioAI-Session-Dev="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("session cookie missing"))
                .split(";", 2)[0];
        String csrfToken = jsonString(csrf.body(), "token");
        assertNotNull(csrfToken);

        String challengeBody = "{\"email\":\"" + email + "\"}";
        HttpResponse<String> challenge = request(
                "POST",
                "/api/auth/registration-challenges",
                challengeBody,
                sessionCookie,
                csrfToken);
        assertEquals(202, challenge.statusCode(), challenge.body());
        String challengeId = jsonString(challenge.body(), "challengeId");
        assertNotNull(challengeId);

        jdbcTemplate.update(
                """
                UPDATE auth_mail_outbox
                SET next_run_at = DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 1 DAY)
                WHERE challenge_id <> ? AND status IN ('PENDING', 'SENDING')
                """,
                jdbcTemplate.queryForObject(
                        "SELECT id FROM auth_verification_challenge WHERE challenge_uid = ?",
                        Long.class,
                        challengeId));

        assertTrue(dispatcher.dispatchOnce());
        String code = awaitMailpitCode(email);
        assertEquals(6, code.length());

        String registerBody = "{\"challengeId\":\"" + challengeId
                + "\",\"code\":\"" + code
                + "\",\"nickname\":\"视频创作者\",\"password\":\"" + password + "\"}";
        HttpResponse<String> registered = request(
                "POST",
                "/api/auth/registrations",
                registerBody,
                sessionCookie,
                csrfToken);
        assertEquals(201, registered.statusCode(), registered.body());
        String userId = jsonString(registered.body(), "userId");
        assertNotNull(userId);
        assertTrue(userId.startsWith("usr_"));
        assertTrue(registered.body().contains("\"roles\":[\"USER\"]"));
        assertTrue(registered.body().contains("\"emailVerified\":true"));

        Integer userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_user WHERE email_norm = ?",
                Integer.class,
                email);
        assertEquals(1, userCount);

        Map<String, Object> credential = jdbcTemplate.queryForMap(
                """
                SELECT c.password_hash, c.credential_version
                FROM auth_password_credential c
                JOIN auth_user u ON u.id = c.user_id
                WHERE u.email_norm = ?
                """,
                email);
        assertTrue(((String) credential.get("password_hash")).contains("$argon2id$"));
        assertEquals(1, ((Number) credential.get("credential_version")).intValue());

        Integer roleCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM auth_user_role ur
                JOIN auth_user u ON u.id = ur.user_id
                JOIN auth_role r ON r.id = ur.role_id
                WHERE u.email_norm = ? AND r.role_code = 'USER'
                """,
                Integer.class,
                email);
        assertEquals(1, roleCount);

        Integer auditCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM auth_audit_event e
                JOIN auth_user u ON u.id = e.subject_user_id
                WHERE u.email_norm = ? AND e.event_type = 'REGISTER' AND e.outcome = 'SUCCESS'
                """,
                Integer.class,
                email);
        assertEquals(1, auditCount);

        String usedStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM auth_verification_challenge WHERE challenge_uid = ?",
                String.class,
                challengeId);
        assertEquals("USED", usedStatus);

        HttpResponse<String> replay = request(
                "POST",
                "/api/auth/registrations",
                registerBody,
                sessionCookie,
                csrfToken);
        assertEquals(400, replay.statusCode());
        assertTrue(replay.body().contains("\"code\":\"CHALLENGE_INVALID\""));

        Integer usersAfterReplay = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_user WHERE email_norm = ?",
                Integer.class,
                email);
        assertEquals(1, usersAfterReplay);

        HttpResponse<String> me = request("GET", "/api/auth/me", null, sessionCookie, null);
        assertEquals(401, me.statusCode());
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
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        return response.body();
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
