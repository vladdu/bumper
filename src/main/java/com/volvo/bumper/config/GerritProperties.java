package com.volvo.bumper.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Gerrit SSH credentials.
 *
 * <p>Bound from environment variables via Spring's relaxed binding: {@code GERRIT_SSH_USER} →
 * {@code user}, {@code GERRIT_SSH_KEY} → {@code key}.
 */
@ConfigurationProperties(prefix = "gerrit.ssh")
public record GerritProperties(String user, String key) {}
