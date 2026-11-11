package com.echeo.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables Spring's scheduled tasks (@Scheduled): reminder e-mails, payment follow-ups,
 * automatic retry of failed e-mails, recurrence roll-forward...
 *
 * Set ECHEO_SCHEDULING_ENABLED=false for local development against a shared or
 * production database: with scheduling off, no e-mail is sent and no data is changed
 * by background jobs. Defaults to true, so deployed environments are unaffected.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "echeo.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}