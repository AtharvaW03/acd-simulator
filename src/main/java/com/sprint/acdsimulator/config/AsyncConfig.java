package com.sprint.acdsimulator.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 📚 LESSON — @EnableAsync + @EnableScheduling:
 *
 * Spring Boot doesn't activate @Async or @Scheduled by default — you must opt in.
 * Placing @EnableAsync / @EnableScheduling on any @Configuration class turns them on
 * for the whole application.
 *
 * 📚 LESSON — ThreadPoolTaskExecutor (for @Async methods):
 *
 * When you call an @Async method, Spring submits it to an Executor (thread pool).
 * The default executor uses a SimpleAsyncTaskExecutor which creates a NEW thread for
 * every call — terrible for high load (thread creation is expensive ~1ms).
 *
 * ThreadPoolTaskExecutor maintains a pool of reusable threads:
 *   corePoolSize    — threads kept alive even when idle (always ready)
 *   maxPoolSize     — maximum threads under burst load
 *   queueCapacity   — tasks that buffer in a LinkedBlockingQueue before new threads spin up
 *
 * Queue fills up BEFORE maxPoolSize threads are created — so set queueCapacity carefully.
 * If queue is also full → RejectedExecutionException (caught by GlobalExceptionHandler → 503).
 *
 * For Java 21 + Virtual Threads we actually set the executor to use virtual threads (see below).
 * This makes @Async methods run on virtual threads, not platform threads.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

    /**
     * Named bean "callIngestionExecutor" — used by @Async("callIngestionExecutor") in controllers.
     *
     * 📚 Virtual Thread Executor:
     *   Executors.newVirtualThreadPerTaskExecutor() creates a new virtual thread per task.
     *   Since virtual threads are cheap (~1KB), there's no need for pooling.
     *   We wrap it in a ThreadPoolTaskExecutor adapter so Spring @Async can use it.
     */
    @Bean("callIngestionExecutor")
    public Executor callIngestionExecutor() {
        // For Java 21: virtual thread per task — no pool needed
        return command -> Thread.ofVirtual()
                .name("call-ingest-", 0)
                .start(command);
    }
}

