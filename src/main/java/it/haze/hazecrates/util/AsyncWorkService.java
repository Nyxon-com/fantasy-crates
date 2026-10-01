package it.haze.hazecrates.util;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Executes blocking I/O only. Bukkit state must stay on the server thread. */
public final class AsyncWorkService implements AutoCloseable {
    private final ExecutorService workers;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(
            Thread.ofVirtual().name("hc-timer-", 0).factory());
    private final AtomicInteger pending = new AtomicInteger();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final int maxPending;
    private final Logger logger;

    public AsyncWorkService(int concurrentActions, int maxPending, Logger logger) {
        this.workers = Executors.newFixedThreadPool(Math.max(1, concurrentActions),
                Thread.ofVirtual().name("hc-io-", 0).factory());
        this.maxPending = Math.max(16, maxPending);
        this.logger = logger;
    }

    public <T> CompletableFuture<T> supply(Callable<T> action) {
        while (true) {
            if (closed.get()) {
                return CompletableFuture.failedFuture(new RejectedExecutionException("Async work service is closed"));
            }
            int count = pending.get();
            if (count >= maxPending) {
                return CompletableFuture.failedFuture(new RejectedExecutionException("Async work queue is full"));
            }
            if (pending.compareAndSet(count, count + 1)) break;
        }
        if (closed.get()) {
            pending.decrementAndGet();
            return CompletableFuture.failedFuture(new RejectedExecutionException("Async work service is closed"));
        }
        CompletableFuture<T> result = new CompletableFuture<>();
        try {
            workers.execute(() -> {
                try {
                    result.complete(action.call());
                } catch (Throwable error) {
                    result.completeExceptionally(error);
                } finally {
                    pending.decrementAndGet();
                }
            });
        } catch (RejectedExecutionException error) {
            pending.decrementAndGet();
            result.completeExceptionally(error);
        }
        return result;
    }

    public CompletableFuture<Void> run(Runnable action) {
        return supply(() -> {
            action.run();
            return null;
        });
    }

    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable action, Duration delay) {
        long millis = Math.max(1L, delay.toMillis());
        return timer.scheduleWithFixedDelay(() -> {
            try {
                action.run();
            } catch (Throwable error) {
                logger.log(Level.SEVERE, "[HazeCrates] Async periodic action failed", error);
            }
        }, millis, millis, TimeUnit.MILLISECONDS);
    }

    public boolean awaitIdle(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (pending.get() > 0 && System.nanoTime() < deadline) {
            try {
                Thread.sleep(Duration.ofMillis(10));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return pending.get() == 0;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        timer.shutdownNow();
        workers.shutdown();
        try {
            if (!workers.awaitTermination(30, TimeUnit.SECONDS)) {
                logger.warning("[HazeCrates] Async work did not finish within 30 seconds.");
                workers.shutdownNow();
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            workers.shutdownNow();
        }
    }
}
