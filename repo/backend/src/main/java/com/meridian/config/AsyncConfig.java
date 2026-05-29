package com.meridian.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Bounds the executor backing {@code @Async} methods (e.g. audit logging).
 *
 * The Spring default is an unbounded SimpleAsyncTaskExecutor that spawns a new thread per
 * task; under a burst of audited actions each task opens a DB connection, which can exhaust
 * the connection pool. A small bounded pool with a queue and a caller-runs fallback keeps
 * concurrent async DB work well under the pool size while never dropping a task.
 */
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        return taskExecutor();
    }

    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("async-");
        // If the queue is full, run the task on the calling thread instead of dropping it.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
