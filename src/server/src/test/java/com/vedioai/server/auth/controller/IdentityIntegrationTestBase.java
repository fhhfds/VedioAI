package com.vedioai.server.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vedioai.server.auth.challenge.ChallengeReceipt;
import com.vedioai.server.auth.challenge.RegistrationChallengeService;
import com.vedioai.server.auth.dto.AuthUserView;
import com.vedioai.server.auth.mail.MailPayloadCipher;
import com.vedioai.server.auth.service.RegistrationService;
import com.vedioai.server.common.web.RequestMetadata;
import java.util.Map;
import java.util.UUID;
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
public abstract class IdentityIntegrationTestBase {

    @Value("${local.server.port}")
    protected int port;

    @Autowired
    protected RegistrationChallengeService challengeService;

    @Autowired
    protected RegistrationService registrationService;

    @Autowired
    protected MailPayloadCipher mailPayloadCipher;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected RegisteredUser registerUser(String nickname) {
        String email = "test+" + UUID.randomUUID().toString().replace("-", "") + "@example.invalid";
        String password = "Passphrase-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "!";
        return registerUser(email, password, nickname);
    }

    protected RegisteredUser registerUser(String email, String password, String nickname) {
        String binding = "binding-" + UUID.randomUUID();
        ChallengeReceipt receipt = challengeService.request(email, binding);
        String code = codeFor(receipt.challengeId());
        AuthUserView user = registrationService.register(
                receipt.challengeId(),
                code,
                nickname,
                password,
                binding,
                new RequestMetadata("127.0.0.1", "IdentityIntegrationTest", "trace-" + UUID.randomUUID()));
        return new RegisteredUser(user, email, password);
    }

    protected String codeFor(String challengeUid) {
        Long challengeId = jdbcTemplate.queryForObject(
                "SELECT id FROM auth_verification_challenge WHERE challenge_uid = ?",
                Long.class,
                challengeUid);
        Map<String, Object> outbox = jdbcTemplate.queryForMap(
                """
                SELECT message_key, payload_cipher, cipher_nonce, cipher_key_id
                FROM auth_mail_outbox WHERE challenge_id = ?
                """,
                challengeId);
        return mailPayloadCipher.decrypt(
                (String) outbox.get("message_key"),
                (String) outbox.get("cipher_key_id"),
                (byte[]) outbox.get("cipher_nonce"),
                (byte[]) outbox.get("payload_cipher"));
    }

    protected TestAccountClient loggedIn(String email, String password) throws Exception {
        TestAccountClient client = new TestAccountClient(port);
        TestAccountClient.Response login = client.login(email, password);
        assertEquals(200, login.status(), login.body());
        TestAccountClient.Response me = client.me();
        assertEquals(200, me.status(), me.body());
        return client;
    }

    protected long databaseUserId(String userUid) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM auth_user WHERE user_uid = ?",
                Long.class,
                userUid);
        if (id == null) {
            throw new AssertionError("user not found: " + userUid);
        }
        return id;
    }

    public record RegisteredUser(AuthUserView view, String email, String password) {
        public String userId() {
            return view.userId();
        }
    }
}
