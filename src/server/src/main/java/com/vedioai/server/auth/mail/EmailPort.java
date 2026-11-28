package com.vedioai.server.auth.mail;

public interface EmailPort {

    void send(EmailMessage message);
}