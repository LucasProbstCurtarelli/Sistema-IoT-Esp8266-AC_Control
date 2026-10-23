package com.iot.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class EntranceSchedulingConfig {

    @Bean(name = "entranceTaskScheduler")
    public TaskScheduler entranceTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("entrada-absence-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler;
    }

    @Bean(name = "entradaPresenceExecutor")
    public Executor entradaPresenceExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "entrada-presenca");
            thread.setDaemon(true);
            return thread;
        });
    }
}
