package com.echeo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Active le support des tâches planifiées (@Scheduled) de Spring,
 * requis pour NotificationSchedulerService.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
