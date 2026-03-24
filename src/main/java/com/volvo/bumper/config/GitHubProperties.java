package com.volvo.bumper.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GitHub API credentials.
 *
 * <p>Bound from environment variables via Spring's relaxed binding: {@code GITHUB_TOKEN} → {@code
 * token}.
 */
@ConfigurationProperties(prefix = "github")
public record GitHubProperties(String token) {}
