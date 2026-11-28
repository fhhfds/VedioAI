package com.vedioai.server.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;


class PasswordResetIT extends IdentityIntegrationTestBase {

    @Test
    void resetPasswordConsumesChallengeAndRevokesOldSessions() throws Exception {
        RegisteredUser user = registerUser("重置测试用户");
        TestAccountClient existingSession = loggedIn(user.email(), user.password());

        TestAccountClient anonymous = new TestAccountClient(port);
        anonymous.refreshCsrf();
        TestAccountClient.Response challenge = anonymous.post(
                "/api/auth/password-reset-challenges",
                "{\"email\":\"" + user.email() + "\"}");
        assertEquals(202, challenge.status(), challenge.body());
        String challengeId = TestAccountClient.jsonString(challenge.body(), "challengeId");
        assertNotNull(challengeId);

        String purpose = jdbcTemplate.queryForObject(
                "SELECT purpose FROM auth_verification_challenge WHERE challenge_uid = ?",
                String.class,
                challengeId);
        assertEquals("RESET", purpose);

        String code = codeFor(challengeId);
        TestAccountClient.Response reset = anonymous.post(
                "/api/auth/password/resets",
                "{\"challengeId\":\"" + challengeId + "\",\"code\":\"" + code
                        + "\",\"newPassword\":\"Reset-Passphrase-42!\"}");
        assertEquals(204, reset.status(), reset.body());

        assertEquals(401, existingSession.me().status());

        TestAccountClient oldLogin = new TestAccountClient(port);
        assertEquals(401, oldLogin.login(user.email(), user.password()).status());

        TestAccountClient newLogin = new TestAccountClient(port);
        assertEquals(200, newLogin.login(user.email(), "Reset-Passphrase-42!").status());
        assertEquals(200, newLogin.me().status());

        TestAccountClient.Response reused = anonymous.post(
                "/api/auth/password/resets",
                "{\"challengeId\":\"" + challengeId + "\",\"code\":\"" + code
                        + "\",\"newPassword\":\"Another-Passphrase-42!\"}");
        assertEquals(400, reused.status(), reused.body());
        assertTrue(reused.body().contains("CHALLENGE_INVALID"));
    }

    @Test
    void unknownEmailGetsSameReceiptButNoChallengeOrMail() throws Exception {
        String email = "missing+" + UUID.randomUUID() + "@example.invalid";
        TestAccountClient anonymous = new TestAccountClient(port);
        anonymous.refreshCsrf();

        TestAccountClient.Response response = anonymous.post(
                "/api/auth/password-reset-challenges",
                "{\"email\":\"" + email + "\"}");

        assertEquals(202, response.status(), response.body());
        assertNotNull(TestAccountClient.jsonString(response.body(), "challengeId"));

        Integer challenges = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_verification_challenge WHERE email_norm = ?",
                Integer.class,
                email);
        Integer outbox = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_mail_outbox WHERE recipient_email_norm = ?",
                Integer.class,
                email);
        assertEquals(0, challenges);
        assertEquals(0, outbox);
    }
}
