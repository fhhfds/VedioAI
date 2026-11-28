package com.vedioai.server.auth.mail;

import com.vedioai.server.auth.config.VedioaiAuthProperties;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;


@Component
public class MailOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(MailOutboxDispatcher.class);
    private static final long LEASE_SECONDS = 120;
    private static final int MAX_BACKOFF_SECONDS = 300;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final MailPayloadCipher mailPayloadCipher;
    private final EmailPort emailPort;
    private final VedioaiAuthProperties properties;

    public MailOutboxDispatcher(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            MailPayloadCipher mailPayloadCipher,
            EmailPort emailPort,
            VedioaiAuthProperties properties) {

        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.mailPayloadCipher = mailPayloadCipher;
        this.emailPort = emailPort;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${vedioai.auth.mail.dispatch-interval-ms:2000}",
            initialDelayString = "1000")
    public void scheduledDispatch() {
        if (!properties.getMail().isDispatcherEnabled()) {
            return;
        }
        try {
            dispatchOnce();
        } catch (RuntimeException exception) {
            log.warn("mail_dispatch_cycle_failed type={}", exception.getClass().getSimpleName());
        }
    }

    public boolean dispatchOnce() {
        ClaimedMail claimed = claim();
        if (claimed == null) {
            return false;
        }

        try {
            String code = mailPayloadCipher.decrypt(
                    claimed.messageKey(),
                    claimed.cipherKeyId(),
                    claimed.cipherNonce(),
                    claimed.payloadCipher());

            EmailContent content = contentFor(claimed.templateCode(), code);
            emailPort.send(new EmailMessage(
                    properties.getMail().getFrom(),
                    claimed.recipientEmail(),
                    content.subject(),
                    content.text()));

            markSent(claimed);
            log.info("mail_dispatch_sent id={} attempt={}", claimed.id(), claimed.attemptNo());
            return true;
        } catch (RuntimeException exception) {
            markFailed(claimed);
            log.warn("mail_dispatch_failed id={} attempt={} type={}",
                    claimed.id(), claimed.attemptNo(), exception.getClass().getSimpleName());
            return false;
        }
    }

    private ClaimedMail claim() {
        return transactionTemplate.execute(status -> {
            List<ClaimedMail> rows = jdbcTemplate.query(
                    """
                    SELECT id, message_key, recipient_email_norm, template_code,
                           payload_cipher, cipher_nonce,
                           cipher_key_id, attempt_no, max_attempts, fence_token
                    FROM auth_mail_outbox
                    WHERE status IN ('PENDING', 'SENDING')
                      AND next_run_at <= UTC_TIMESTAMP(3)
                      AND (status = 'PENDING' OR lease_until IS NULL OR lease_until < UTC_TIMESTAMP(3))
                    ORDER BY id
                    LIMIT 1
                    FOR UPDATE SKIP LOCKED
                    """,
                    (rs, rowNum) -> new ClaimedMail(
                            rs.getLong("id"),
                            rs.getString("message_key"),
                            rs.getString("recipient_email_norm"),
                            rs.getString("template_code"),
                            rs.getBytes("payload_cipher"),
                            rs.getBytes("cipher_nonce"),
                            rs.getString("cipher_key_id"),
                            rs.getInt("attempt_no"),
                            rs.getInt("max_attempts"),
                            rs.getLong("fence_token")));

            if (rows.isEmpty()) {
                return null;
            }

            ClaimedMail row = rows.get(0);
            int nextAttempt = row.attemptNo() + 1;
            if (nextAttempt > row.maxAttempts()) {
                jdbcTemplate.update(
                        """
                        UPDATE auth_mail_outbox
                        SET status = 'FAILED', last_error_code = 'MAX_ATTEMPTS',
                            lease_owner = NULL, lease_until = NULL,
                            updated_at = UTC_TIMESTAMP(3)
                        WHERE id = ?
                        """,
                        row.id());
                return null;
            }

            long fenceToken = row.fenceToken() + 1;
            String executionId = UUID.randomUUID().toString();
            jdbcTemplate.update(
                    """
                    UPDATE auth_mail_outbox
                    SET status = 'SENDING',
                        attempt_no = ?,
                        execution_id = ?,
                        fence_token = ?,
                        lease_owner = 'mail-dispatcher',
                        lease_until = ?,
                        updated_at = UTC_TIMESTAMP(3)
                    WHERE id = ?
                    """,
                    nextAttempt,
                    executionId,
                    fenceToken,
                    Timestamp.from(Instant.now().plusSeconds(LEASE_SECONDS)),
                    row.id());

            return new ClaimedMail(
                    row.id(),
                    row.messageKey(),
                    row.recipientEmail(),
                    row.templateCode(),
                    row.payloadCipher(),
                    row.cipherNonce(),
                    row.cipherKeyId(),
                    nextAttempt,
                    row.maxAttempts(),
                    fenceToken);
        });
    }

    private void markSent(ClaimedMail claimed) {
        transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update(
                """
                UPDATE auth_mail_outbox
                SET status = 'SENT',
                    sent_at = UTC_TIMESTAMP(3),
                    payload_cipher = NULL,
                    cipher_nonce = NULL,
                    cipher_key_id = NULL,
                    lease_owner = NULL,
                    lease_until = NULL,
                    last_error_code = NULL,
                    updated_at = UTC_TIMESTAMP(3)
                WHERE id = ? AND fence_token = ? AND status = 'SENDING'
                """,
                claimed.id(),
                claimed.fenceToken()));
    }

    private void markFailed(ClaimedMail claimed) {
        boolean exhausted = claimed.attemptNo() >= claimed.maxAttempts();
        String status = exhausted ? "FAILED" : "PENDING";
        int backoffSeconds = Math.min(MAX_BACKOFF_SECONDS, 10 * Math.max(1, claimed.attemptNo()));

        transactionTemplate.executeWithoutResult(transactionStatus -> jdbcTemplate.update(
                """
                UPDATE auth_mail_outbox
                SET status = ?,
                    next_run_at = ?,
                    last_error_code = 'SMTP_ERROR',
                    lease_owner = NULL,
                    lease_until = NULL,
                    updated_at = UTC_TIMESTAMP(3)
                WHERE id = ? AND fence_token = ? AND status = 'SENDING'
                """,
                status,
                Timestamp.from(Instant.now().plus(Duration.ofSeconds(backoffSeconds))),
                claimed.id(),
                claimed.fenceToken()));
    }

    private EmailContent contentFor(String templateCode, String code) {
        long minutes = Math.max(1, properties.getChallenge().getTtl().toMinutes());
        if ("RESET_CODE".equals(templateCode)) {
            return new EmailContent(
                    "VedioAI 密码重置验证码",
                    "您的 VedioAI 密码重置验证码是：" + code + "，" + minutes
                            + " 分钟内有效。如非本人操作，请忽略本邮件。");
        }
        return new EmailContent(
                "VedioAI 注册验证码",
                "您的 VedioAI 注册验证码是：" + code + "，" + minutes
                        + " 分钟内有效。请勿向他人泄露。");
    }

    private record EmailContent(String subject, String text) {
    }

    private record ClaimedMail(
            long id,
            String messageKey,
            String recipientEmail,
            String templateCode,
            byte[] payloadCipher,
            byte[] cipherNonce,
            String cipherKeyId,
            int attemptNo,
            int maxAttempts,
            long fenceToken) {
    }
}
