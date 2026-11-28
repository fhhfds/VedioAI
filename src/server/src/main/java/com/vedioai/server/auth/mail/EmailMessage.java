package com.vedioai.server.auth.mail;

public record EmailMessage(String from, String to, String subject, String text) {
}