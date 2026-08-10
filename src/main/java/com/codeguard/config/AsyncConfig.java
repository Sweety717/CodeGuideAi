package com.codeguard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {

    /**
     * Small dedicated pool for webhook-triggered reviews. Kept modest since
     * AI review calls are the bottleneck (seconds each), not CPU-bound work -
     * a large pool wouldn't speed anything up, just risk hitting AI provider
     * rate limits faster.
     */
    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("review-");
        executor.initialize();
        return executor;
    }
}
