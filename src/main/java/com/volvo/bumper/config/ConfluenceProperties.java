package com.volvo.bumper.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Confluence Cloud API credentials and page references.
 *
 * <p>Bound from environment variables via Spring's relaxed binding:
 *
 * <ul>
 *   <li>{@code CONFLUENCE_URL} → {@code url}
 *   <li>{@code CONFLUENCE_USERNAME} → {@code username}
 *   <li>{@code CONFLUENCE_API_TOKEN} → {@code apiToken}
 *   <li>{@code CONFLUENCE_PAGE_ID_READ} → {@code pageIdRead}
 *   <li>{@code CONFLUENCE_PAGE_ID_WRITE} → {@code pageIdWrite}
 * </ul>
 */
@ConfigurationProperties(prefix = "confluence")
@Validated
public record ConfluenceProperties(
    @NotBlank String url,
    @NotBlank String username,
    @NotBlank String apiToken,
    @NotBlank String pageIdRead,
    @NotBlank String pageIdWrite) {}
