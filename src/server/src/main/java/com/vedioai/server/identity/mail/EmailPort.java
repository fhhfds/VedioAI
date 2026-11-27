package com.vedioai.server.identity.mail;

public interface EmailPort {

    void send(EmailMessage message);
}