package com.vedioai.server.identity.mail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import com.vedioai.server.identity.challenge.ChallengeReceipt;
import com.vedioai.server.identity.challenge.RegistrationChallengeService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "vedioai.auth.mail.dispatcher-enabled=false",
                "vedioai.test.mail-port=fake"
        })
@Import(MailOutboxRetryIT.TestEmailConfig.class)
class MailOutboxRetryIT {

    @TestConfiguration
    static class TestEmailConfig {

        @Bean
        @Primary
        TestEmailPort testEmailPort() {
            return new TestEmailPort();
        }
    }

    static class TestEmailPort implements EmailPort {

        volatile boolean fail = true;

        @Override
        public void send(EmailMessage message) {
            if (fail) {
                throw new IllegalStateException("simulated SMTP failure");
            }
        }
    }

    @Autowired
    private RegistrationChallengeService challengeService;

    @Autowired
    private MailOutboxDispatcher dispatcher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TestEmailPort emailPort;

    @Test
    void retriesWithBackoffAndSendsAfterSmtpRecovers() {
        String email = "retry+" + UUID.randomUUID().toString().replace("-", "") + "@example.invalid";
        ChallengeReceipt receipt = challengeService.request(email, "binding-" + UUID.randomUUID());

        Long challengeId = jdbcTemplate.queryForObject(
                "SELECT id FROM auth_verification_challenge WHERE challenge_uid = ?",
                Long.class,
                receipt.challengeId());
        assertNotNull(challengeId);

        jdbcTemplate.update(
                """
                UPDATE auth_mail_outbox
                SET next_run_at = DATE_ADD(UTC_TIMESTAMP(3), INTERVAL 1 DAY)
                WHERE challenge_id <> ? AND status IN ('PENDING', 'SENDING')
                """,
                challengeId);

        emailPort.fail = true;
        assertFalse(dispatcher.dispatchOnce());

        Map<String, Object> failed = jdbcTemplate.queryForMap(
                """
                SELECT status, attempt_no, last_error_code,
                       (next_run_at > UTC_TIMESTAMP(3)) AS run_at_is_future
                FROM auth_mail_outbox WHERE challenge_id = ?
                """,
                challengeId);
        assertEquals("PENDING", failed.get("status"));
        assertEquals(1, ((Number) failed.get("attempt_no")).intValue());
        assertEquals("SMTP_ERROR", failed.get("last_error_code"));
        assertEquals(1, ((Number) failed.get("run_at_is_future")).intValue());

        jdbcTemplate.update(
                "UPDATE auth_mail_outbox SET next_run_at = UTC_TIMESTAMP(3) WHERE challenge_id = ?",
                challengeId);

        emailPort.fail = false;
        assertTrue(dispatcher.dispatchOnce());

        Map<String, Object> sent = jdbcTemplate.queryForMap(
                "SELECT status, payload_cipher FROM auth_mail_outbox WHERE challenge_id = ?",
                challengeId);
        assertEquals("SENT", sent.get("status"));
        assertEquals(null, sent.get("payload_cipher"));
    }
}