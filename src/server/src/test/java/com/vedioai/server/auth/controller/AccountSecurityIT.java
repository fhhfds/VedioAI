package com.vedioai.server.auth.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.jupiter.api.Test;


class AccountSecurityIT extends IdentityIntegrationTestBase {

    @Test
    void profileReauthSessionsAndPasswordChange() throws Exception {
        RegisteredUser user = registerUser("安全测试用户");
        TestAccountClient client = loggedIn(user.email(), user.password());

        TestAccountClient.Response me = client.me();
        long version = TestAccountClient.jsonLong(me.body(), "version");
        String sessionId = TestAccountClient.jsonString(me.body(), "sessionId");
        assertNotNull(sessionId);
        byte[] storedHash = jdbcTemplate.queryForObject(
                "SELECT session_id_sha256 FROM auth_session_guard WHERE session_uid = ?",
                byte[].class,
                sessionId);
        String cookieValue = client.cookie().substring("VedioAI-Session-Dev=".length());
        String frameworkSessionId = new String(
                java.util.Base64.getDecoder().decode(cookieValue), StandardCharsets.UTF_8);
        byte[] computedHash = MessageDigest.getInstance("SHA-256")
                .digest(frameworkSessionId.getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(computedHash, storedHash);

        TestAccountClient.Response stale = client.patch(
                "/api/auth/profile",
                "{\"nickname\":\"新昵称\",\"expectedVersion\":999}");
        assertEquals(409, stale.status(), stale.body());
        assertTrue(stale.body().contains("VERSION_CONFLICT"));

        TestAccountClient.Response profile = client.patch(
                "/api/auth/profile",
                "{\"nickname\":\"新昵称\",\"expectedVersion\":" + version + "}");
        assertEquals(200, profile.status(), profile.body());
        assertTrue(profile.body().contains("\"nickname\":\"新昵称\""));
        assertTrue(profile.body().contains("\"version\":" + (version + 1)));

        TestAccountClient.Response sessions = client.get("/api/auth/sessions");
        assertEquals(200, sessions.status(), sessions.body());
        assertTrue(sessions.body().contains("\"current\":true"));
        assertTrue(sessions.body().contains(sessionId));

        TestAccountClient.Response wrongReauth = client.post(
                "/api/auth/reauthentication",
                "{\"password\":\"Wrong-Passphrase-99\"}");
        assertEquals(400, wrongReauth.status(), wrongReauth.body());
        assertTrue(wrongReauth.body().contains("CURRENT_PASSWORD_INVALID"));
        assertEquals(200, client.me().status());

        TestAccountClient.Response reauth = client.post(
                "/api/auth/reauthentication",
                "{\"password\":\"" + user.password() + "\"}");
        assertEquals(200, reauth.status(), reauth.body());
        assertNotNull(TestAccountClient.jsonString(reauth.body(), "validUntil"));

        TestAccountClient.Response wrongChange = client.post(
                "/api/auth/password/change",
                "{\"currentPassword\":\"Wrong-Passphrase-99\",\"newPassword\":\"New-Passphrase-42!\"}");
        assertEquals(400, wrongChange.status(), wrongChange.body());
        assertTrue(wrongChange.body().contains("CURRENT_PASSWORD_INVALID"));
        assertEquals(200, client.me().status());

        TestAccountClient.Response changed = client.post(
                "/api/auth/password/change",
                "{\"currentPassword\":\"" + user.password()
                        + "\",\"newPassword\":\"New-Passphrase-42!\"}");
        assertEquals(204, changed.status(), changed.body());
        assertEquals(401, client.me().status());

        long userId = databaseUserId(user.userId());
        String reason = jdbcTemplate.queryForObject(
                "SELECT revocation_reason FROM auth_session_guard WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                String.class,
                userId);
        assertEquals("PASSWORD", reason);

        TestAccountClient oldLogin = new TestAccountClient(port);
        assertEquals(401, oldLogin.login(user.email(), user.password()).status());

        TestAccountClient newLogin = new TestAccountClient(port);
        assertEquals(200, newLogin.login(user.email(), "New-Passphrase-42!").status());
        assertEquals(200, newLogin.me().status());
    }

    @Test
    void revokeAnotherSessionAndRevokeAllRequiresReauth() throws Exception {
        RegisteredUser user = registerUser("会话测试用户");
        TestAccountClient first = loggedIn(user.email(), user.password());
        TestAccountClient second = loggedIn(user.email(), user.password());

        String secondSession = TestAccountClient.jsonString(second.me().body(), "sessionId");
        TestAccountClient.Response list = first.get("/api/auth/sessions");
        assertEquals(200, list.status(), list.body());
        assertTrue(list.body().contains(secondSession));

        assertEquals(204, first.delete("/api/auth/sessions/" + secondSession).status());
        assertEquals(401, second.me().status());
        assertEquals(200, first.me().status());

        TestAccountClient.Response noProof = first.post("/api/auth/sessions/revoke-all", null);
        assertEquals(403, noProof.status(), noProof.body());
        assertTrue(noProof.body().contains("REAUTH_REQUIRED"));

        assertEquals(200, first.post(
                "/api/auth/reauthentication",
                "{\"password\":\"" + user.password() + "\"}").status());
        assertEquals(204, first.post("/api/auth/sessions/revoke-all", null).status());
        assertEquals(401, first.me().status());

        Integer revokedAll = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM auth_session_guard
                WHERE user_id = ? AND status = 'REVOKED' AND revocation_reason = 'ALL'
                """,
                Integer.class,
                databaseUserId(user.userId()));
        assertEquals(1, revokedAll);
    }

    @Test
    void everyPrivateRequestChecksGuardLedger() throws Exception {
        RegisteredUser user = registerUser("守卫测试用户");
        TestAccountClient client = loggedIn(user.email(), user.password());
        String sessionId = TestAccountClient.jsonString(client.me().body(), "sessionId");

        jdbcTemplate.update(
                """
                UPDATE auth_session_guard
                SET status = 'REVOKED', revoked_at = UTC_TIMESTAMP(3),
                    revocation_reason = 'TEST', updated_at = UTC_TIMESTAMP(3)
                WHERE session_uid = ?
                """,
                sessionId);

        TestAccountClient.Response response = client.get("/api/auth/sessions");
        assertEquals(401, response.status(), response.body());
        assertTrue(response.body().contains("SESSION_EXPIRED"));
        assertFalse(response.body().isBlank());
    }
}
