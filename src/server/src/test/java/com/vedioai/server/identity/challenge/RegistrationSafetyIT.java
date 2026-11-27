package com.vedioai.server.identity.challenge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.vedioai.server.identity.mail.MailPayloadCipher;
import com.vedioai.server.identity.web.AuthUserView;
import com.vedioai.server.identity.web.RequestMetadata;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "vedioai.auth.mail.dispatcher-enabled=false")
class RegistrationSafetyIT {

    private static final String PASSWORD = "Cloud rivers cross quiet meadows 42!";

    @Autowired
    private RegistrationChallengeService challengeService;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private MailPayloadCipher mailPayloadCipher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void duplicateEmailIsRejectedOnlyAfterValidProof() {
        String email = uniqueEmail();
        IssuedCode first = issueCode(email, "binding-" + UUID.randomUUID());
        registrationService.register(first.challengeId(), first.code(), "首位用户",
                PASSWORD, first.binding(), metadata());

        IssuedCode second = issueCode(email, "binding-" + UUID.randomUUID());
        assertThrows(EmailAlreadyRegisteredException.class, () -> registrationService.register(
                second.challengeId(), second.code(), "重复用户",
                PASSWORD, second.binding(), metadata()));

        assertEquals(1, userCount(email));
    }

    @Test
    void wrongCodeConsumesAttemptButCorrectCodeStillWorks() {
        String email = uniqueEmail();
        IssuedCode issued = issueCode(email, "binding-" + UUID.randomUUID());
        String wrong = "00000000".equals(issued.code()) ? "00000001" : "00000000";

        assertThrows(ChallengeInvalidException.class, () -> registrationService.register(
                issued.challengeId(), wrong, "错误码用户",
                PASSWORD, issued.binding(), metadata()));

        Integer attempts = jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM auth_verification_challenge WHERE challenge_uid = ?",
                Integer.class,
                issued.challengeId());
        assertEquals(1, attempts);

        AuthUserView user = registrationService.register(
                issued.challengeId(), issued.code(), "正确码用户",
                PASSWORD, issued.binding(), metadata());
        assertNotNull(user);
        assertEquals(1, userCount(email));
    }

    @Test
    void anotherBrowserBindingCannotConsumeChallenge() {
        String email = uniqueEmail();
        IssuedCode issued = issueCode(email, "binding-" + UUID.randomUUID());

        assertThrows(ChallengeInvalidException.class, () -> registrationService.register(
                issued.challengeId(), issued.code(), "其他浏览器",
                PASSWORD, "binding-" + UUID.randomUUID(), metadata()));
        assertEquals(0, userCount(email));
    }

    @Test
    void concurrentRegistrationCreatesOnlyOneUser() throws Exception {
        String email = uniqueEmail();
        IssuedCode issued = issueCode(email, "binding-" + UUID.randomUUID());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = pool.submit(() -> attemptRegistration(issued, "并发用户一", start));
            Future<Boolean> second = pool.submit(() -> attemptRegistration(issued, "并发用户二", start));
            start.countDown();

            boolean firstResult = first.get();
            boolean secondResult = second.get();

            assertTrue(firstResult ^ secondResult);
            assertEquals(1, userCount(email));
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean attemptRegistration(IssuedCode issued, String nickname, CountDownLatch start)
            throws Exception {
        start.await();
        try {
            registrationService.register(issued.challengeId(), issued.code(), nickname,
                    PASSWORD, issued.binding(), metadata());
            return true;
        } catch (ChallengeInvalidException | EmailAlreadyRegisteredException exception) {
            return false;
        }
    }

    private IssuedCode issueCode(String email, String binding) {
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

        return new IssuedCode(receipt.challengeId(), code, binding);
    }

    private int userCount(String email) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_user WHERE email_norm = ?",
                Integer.class,
                email);
        return count == null ? 0 : count;
    }

    private static String uniqueEmail() {
        return "safety+" + UUID.randomUUID().toString().replace("-", "") + "@example.invalid";
    }

    private static RequestMetadata metadata() {
        return new RequestMetadata("127.0.0.1", "RegistrationSafetyIT", "trace-" + UUID.randomUUID());
    }

    private record IssuedCode(String challengeId, String code, String binding) {
    }
}