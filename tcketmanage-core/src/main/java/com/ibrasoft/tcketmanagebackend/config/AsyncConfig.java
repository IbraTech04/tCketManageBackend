package com.ibrasoft.tcketmanagebackend.config;

import com.ibrasoft.tcketmanagebackend.service.EmailProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Enables {@code @Async} and supplies the dedicated executor ticket emails run on. Keeping email off
 * the request/fulfillment threads is the whole point: SMTP connect + Batik PNG render can take
 * seconds per ticket, so a bulk resend would otherwise block (and time out) the HTTP request.
 *
 * <p>The pool is intentionally small and bounded (see {@link EmailProperties.Async}). On queue
 * overflow we fall back to {@link ThreadPoolExecutor.CallerRunsPolicy} so sends are throttled rather
 * than dropped.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("emailExecutor")
    public Executor emailExecutor(EmailProperties properties) {
        EmailProperties.Async async = properties.getAsync();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(async.getConcurrency());
        executor.setMaxPoolSize(async.getConcurrency());
        executor.setQueueCapacity(async.getQueueCapacity());
        executor.setThreadNamePrefix("email-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
