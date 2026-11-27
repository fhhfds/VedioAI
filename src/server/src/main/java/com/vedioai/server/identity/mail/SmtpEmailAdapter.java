package com.vedioai.server.identity.mail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import com.vedioai.server.identity.config.VedioaiAuthProperties;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailAdapter implements EmailPort {

    private final JavaMailSender mailSender;

    public SmtpEmailAdapter(JavaMailSender localMailSender, VedioaiAuthProperties properties) {
        String deliveryMode = properties.getMail().getDeliveryMode();

        if ("local-catcher".equals(deliveryMode)) {
            this.mailSender = localMailSender;
            return;
        }

        if ("smtp".equals(deliveryMode)) {
            this.mailSender = buildSmtpSender(properties.getMail().getSmtp());
            return;
        }

        throw new IllegalStateException("Unsupported mail delivery mode: " + deliveryMode);
    }

    @Override
    public void send(EmailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(message.from());
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.text());
        mailSender.send(mail);
    }

    private JavaMailSender buildSmtpSender(VedioaiAuthProperties.Mail.Smtp smtp) {
        if (isBlank(smtp.getHost()) || isBlank(smtp.getUsername())) {
            throw new IllegalStateException(
                    "vedioai.auth.mail.smtp.host and username are required when delivery-mode=smtp");
        }

        String password = readPassword(smtp.getPasswordFile());

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(smtp.getHost());
        sender.setPort(smtp.getPort());
        sender.setUsername(smtp.getUsername());
        sender.setPassword(password);
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", Boolean.toString(smtp.isStarttls()));
        properties.put("mail.smtp.starttls.required", Boolean.toString(smtp.isStarttls()));
        properties.put("mail.smtp.ssl.enable", Boolean.toString(smtp.isSsl()));
        properties.put("mail.smtp.ssl.trust", smtp.getHost());
        properties.put("mail.smtp.connectiontimeout", "5000");
        properties.put("mail.smtp.timeout", "5000");
        properties.put("mail.smtp.writetimeout", "5000");

        return sender;
    }

    private String readPassword(String passwordFile) {
        if (isBlank(passwordFile)) {
            throw new IllegalStateException("vedioai.auth.mail.smtp.password-file is required");
        }

        Path path = Path.of(passwordFile);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException(
                    "SMTP password file does not exist: " + path.toAbsolutePath());
        }

        try {
            String password = Files.readString(path, StandardCharsets.UTF_8).trim();
            if (password.isEmpty()) {
                throw new IllegalStateException("SMTP password file is empty");
            }
            return password;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read SMTP password file", exception);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}