package com.vedioai.server.auth.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;


@Validated
@ConfigurationProperties(prefix = "vedioai.auth")
public class VedioaiAuthProperties {

    @Valid
    private Cookie cookie = new Cookie();

    @Valid
    private Session session = new Session();

    @Valid
    private Reauthentication reauthentication = new Reauthentication();

    @Valid
    private Password password = new Password();

    @Valid
    private Challenge challenge = new Challenge();

    @Valid
    private Mail mail = new Mail();

    @Valid
    private AdminBootstrap adminBootstrap = new AdminBootstrap();

    private boolean registrationEnabled = false;

    public Cookie getCookie() {
        return cookie;
    }

    public void setCookie(Cookie cookie) {
        this.cookie = cookie;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public Reauthentication getReauthentication() {
        return reauthentication;
    }

    public void setReauthentication(Reauthentication reauthentication) {
        this.reauthentication = reauthentication;
    }

    public Password getPassword() {
        return password;
    }

    public void setPassword(Password password) {
        this.password = password;
    }

    public Challenge getChallenge() {
        return challenge;
    }

    public void setChallenge(Challenge challenge) {
        this.challenge = challenge;
    }

    public Mail getMail() {
        return mail;
    }

    public void setMail(Mail mail) {
        this.mail = mail;
    }

    public AdminBootstrap getAdminBootstrap() {
        return adminBootstrap;
    }

    public void setAdminBootstrap(AdminBootstrap adminBootstrap) {
        this.adminBootstrap = adminBootstrap;
    }

    public boolean isRegistrationEnabled() {
        return registrationEnabled;
    }

    public void setRegistrationEnabled(boolean registrationEnabled) {
        this.registrationEnabled = registrationEnabled;
    }

    public static class Cookie {

        @NotBlank
        private String name = "VedioAI-Session-Dev";

        private boolean secure = false;

        private boolean httpOnly = true;

        @NotBlank
        private String sameSite = "Lax";

        @NotBlank
        private String path = "/";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isSecure() {
            return secure;
        }

        public void setSecure(boolean secure) {
            this.secure = secure;
        }

        public boolean isHttpOnly() {
            return httpOnly;
        }

        public void setHttpOnly(boolean httpOnly) {
            this.httpOnly = httpOnly;
        }

        public String getSameSite() {
            return sameSite;
        }

        public void setSameSite(String sameSite) {
            this.sameSite = sameSite;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    public static class Session {

        @NotNull
        private Duration absoluteTtl = Duration.ofHours(12);

        @Min(1)
        @Max(50)
        private int maxActive = 5;

        public Duration getAbsoluteTtl() {
            return absoluteTtl;
        }

        public void setAbsoluteTtl(Duration absoluteTtl) {
            this.absoluteTtl = absoluteTtl;
        }

        public int getMaxActive() {
            return maxActive;
        }

        public void setMaxActive(int maxActive) {
            this.maxActive = maxActive;
        }
    }

    public static class Reauthentication {

        @NotNull
        private Duration ttl = Duration.ofMinutes(5);

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }
    }

    public static class Password {

        @Valid
        private Argon2 argon2 = new Argon2();

        public Argon2 getArgon2() {
            return argon2;
        }

        public void setArgon2(Argon2 argon2) {
            this.argon2 = argon2;
        }

        public static class Argon2 {

            @Min(8192)
            @Max(1048576)
            private int memoryKib = 65536;

            @Min(1)
            @Max(10)
            private int iterations = 3;

            @Min(1)
            @Max(8)
            private int parallelism = 1;

            public int getMemoryKib() {
                return memoryKib;
            }

            public void setMemoryKib(int memoryKib) {
                this.memoryKib = memoryKib;
            }

            public int getIterations() {
                return iterations;
            }

            public void setIterations(int iterations) {
                this.iterations = iterations;
            }

            public int getParallelism() {
                return parallelism;
            }

            public void setParallelism(int parallelism) {
                this.parallelism = parallelism;
            }
        }
    }

    public static class Challenge {

        @Min(6)
        @Max(10)
        private int otpLength = 6;

        @NotNull
        private Duration ttl = Duration.ofMinutes(10);

        @Min(1)
        @Max(10)
        private int maxAttempts = 5;

        @NotNull
        private Duration resendAfter = Duration.ofSeconds(60);

        @NotBlank
        private String hmacKeyId;

        @NotBlank
        private String hmacKeyBase64;

        @NotBlank
        private String bindingKeyId;

        @NotBlank
        private String bindingKeyBase64;

        public int getOtpLength() {
            return otpLength;
        }

        public void setOtpLength(int otpLength) {
            this.otpLength = otpLength;
        }

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getResendAfter() {
            return resendAfter;
        }

        public void setResendAfter(Duration resendAfter) {
            this.resendAfter = resendAfter;
        }

        public String getHmacKeyId() {
            return hmacKeyId;
        }

        public void setHmacKeyId(String hmacKeyId) {
            this.hmacKeyId = hmacKeyId;
        }

        public String getHmacKeyBase64() {
            return hmacKeyBase64;
        }

        public void setHmacKeyBase64(String hmacKeyBase64) {
            this.hmacKeyBase64 = hmacKeyBase64;
        }

        public String getBindingKeyId() {
            return bindingKeyId;
        }

        public void setBindingKeyId(String bindingKeyId) {
            this.bindingKeyId = bindingKeyId;
        }

        public String getBindingKeyBase64() {
            return bindingKeyBase64;
        }

        public void setBindingKeyBase64(String bindingKeyBase64) {
            this.bindingKeyBase64 = bindingKeyBase64;
        }
    }

    public static class AdminBootstrap {

        private boolean enabled = false;

        private String userId = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }
    }

    public static class Mail {

        @NotBlank
        private String from;

        @NotBlank
        private String deliveryMode = "local-catcher";

        @Valid
        private Smtp smtp = new Smtp();

        @NotBlank
        private String payloadKeyId;

        @NotBlank
        private String payloadKeyBase64;

        private boolean dispatcherEnabled = true;

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getDeliveryMode() {
            return deliveryMode;
        }

        public void setDeliveryMode(String deliveryMode) {
            this.deliveryMode = deliveryMode;
        }

        public Smtp getSmtp() {
            return smtp;
        }

        public void setSmtp(Smtp smtp) {
            this.smtp = smtp;
        }

        public String getPayloadKeyId() {
            return payloadKeyId;
        }

        public void setPayloadKeyId(String payloadKeyId) {
            this.payloadKeyId = payloadKeyId;
        }

        public String getPayloadKeyBase64() {
            return payloadKeyBase64;
        }

        public void setPayloadKeyBase64(String payloadKeyBase64) {
            this.payloadKeyBase64 = payloadKeyBase64;
        }

        public boolean isDispatcherEnabled() {
            return dispatcherEnabled;
        }

        public void setDispatcherEnabled(boolean dispatcherEnabled) {
            this.dispatcherEnabled = dispatcherEnabled;
        }

        public static class Smtp {

            private String host = "smtp.qq.com";
            private int port = 587;
            private String username = "";
            private String passwordFile = "config/secrets/qq-smtp-password.txt";
            private boolean starttls = true;
            private boolean ssl = false;

            public String getHost() {
                return host;
            }

            public void setHost(String host) {
                this.host = host;
            }

            public int getPort() {
                return port;
            }

            public void setPort(int port) {
                this.port = port;
            }

            public String getUsername() {
                return username;
            }

            public void setUsername(String username) {
                this.username = username;
            }

            public String getPasswordFile() {
                return passwordFile;
            }

            public void setPasswordFile(String passwordFile) {
                this.passwordFile = passwordFile;
            }

            public boolean isStarttls() {
                return starttls;
            }

            public void setStarttls(boolean starttls) {
                this.starttls = starttls;
            }

            public boolean isSsl() {
                return ssl;
            }

            public void setSsl(boolean ssl) {
                this.ssl = ssl;
            }
        }
    }
}
