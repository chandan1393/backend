package com.assignease.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async configuration for background email sending.
 *
 * Why async for emails:
 * - SMTP handshake + TLS negotiation takes 200ms–2s
 * - User should NEVER wait for email delivery to get their API response
 * - Emails are best-effort — failures are logged, not thrown to the caller
 *
 * Thread pool sizing:
 * - corePool=2:  always-alive threads for normal load
 * - maxPool=8:   burst capacity (e.g. bulk welcome emails)
 * - queue=100:   buffer for brief spikes; drops task if full (email loss
 *                is acceptable vs blocking the request thread)
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    @Bean(name = "emailTaskExecutor")
    public Executor emailTaskExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(8);
        exec.setQueueCapacity(100);
        exec.setThreadNamePrefix("email-");
        exec.setWaitForTasksToCompleteOnShutdown(false); // don't delay shutdown for pending emails
        exec.initialize();
        return exec;
    }

    @Override
    public Executor getAsyncExecutor() {
        return emailTaskExecutor();
    }

    /**
     * Log exceptions from @Async methods silently — never propagate to caller.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
            log.error("Async email task failed in {}(): {}", method.getName(), ex.getMessage());
    }
}
