package com.vedioai.server.identity.mail;

public record EmailMessage(String from, String to, String subject, String text) {
}