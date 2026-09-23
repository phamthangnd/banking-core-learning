package com.example.bankcore.notification.application;

/**
 * Sending an email.
 *
 * <p>A port with a deliberately small surface. Delivery is someone else's problem — SMTP in
 * development, a provider API in production — and the domain should not learn about either.
 * It also keeps tests from sending anything: they bind a recording implementation instead.
 */
public interface EmailSender {

    /**
     * @param to      recipient address
     * @param subject subject line
     * @param body    plain-text body; never contains credentials, tokens or balances
     */
    void send(String to, String subject, String body);
}
