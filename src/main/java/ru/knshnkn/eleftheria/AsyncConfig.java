package ru.knshnkn.eleftheria;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "customExecutor")
    public Executor customExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(500);
        executor.setMaxPoolSize(500);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("UserBot-");
        executor.initialize();
        return executor;
    }
}