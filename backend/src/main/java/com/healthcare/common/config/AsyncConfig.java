package com.healthcare.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {
    // Virtual threads (enabled in application.yml via spring.threads.virtual.enabled=true)
    // handle thread management. No custom executor needed for MVP scale.
}
