package com.example;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.pool.InstrumentedPool;
import reactor.pool.PoolBuilder;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UsingWhenBugTest {

    private static final int COUNT = 10_000;

    private final InstrumentedPool<String> stringReactivePool = PoolBuilder
            .from(Mono.just("value").delayElement(Duration.ofMillis(2)))
            .maxPendingAcquireUnbounded()
            .sizeBetween(0, 3)
            .buildPool();

    @Test
    public void reactorPoolBug() throws InterruptedException {
        ExecutorService loggerThreads = Executors.newFixedThreadPool(
                1,
                r -> {
                    Thread t = Executors.defaultThreadFactory().newThread(r);
                    t.setDaemon(true);
                    return t;
                }
        );

        loggerThreads.submit(new PoolMetricsLogger(stringReactivePool.metrics()));

        ExecutorService executorService = Executors.newFixedThreadPool(
                16,
                r -> {
                    Thread t = Executors.defaultThreadFactory().newThread(r);
                    t.setDaemon(true);
                    return t;
                }
        );

        CountDownLatch cdl = new CountDownLatch(COUNT);

        AtomicInteger acquireCount = new AtomicInteger();
        AtomicInteger releaseCount = new AtomicInteger();
        AtomicInteger errorReleaseCount = new AtomicInteger();
        AtomicInteger cancelReleaseCount = new AtomicInteger();

        for (int i = 0; i < COUNT; i++) {
            executorService.submit(new FlatMapErrorTask(cdl, acquireCount, releaseCount, errorReleaseCount, cancelReleaseCount));
        }

        cdl.await();

        assertEquals(10 * COUNT, acquireCount.get());
        assertEquals(10 * COUNT, releaseCount.get() + errorReleaseCount.get() + cancelReleaseCount.get());
    }

    private static final class PoolMetricsLogger implements Runnable {

        private final InstrumentedPool.PoolMetrics poolMetrics;

        private PoolMetricsLogger(InstrumentedPool.PoolMetrics poolMetrics) {
            this.poolMetrics = poolMetrics;
        }

        public void run() {
            while (true) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                System.err.printf(
                        "[POOL Metrics] Acquired = %d Pending = %d Idle = %d%n",
                        poolMetrics.acquiredSize(),
                        poolMetrics.pendingAcquireSize(),
                        poolMetrics.idleSize()
                );
            }
        }
    }

    private static final class FlatMapErrorTask implements Runnable {

        private final CountDownLatch cdl;
        private final AtomicInteger acquireCount;
        private final AtomicInteger releaseCount;
        private final AtomicInteger errorReleaseCount;
        private final AtomicInteger cancelReleaseCount;

        public FlatMapErrorTask(CountDownLatch cdl, AtomicInteger acquireCount, AtomicInteger releaseCount, AtomicInteger errorReleaseCount, AtomicInteger cancelReleaseCount) {
            this.cdl = cdl;
            this.acquireCount = acquireCount;
            this.releaseCount = releaseCount;
            this.errorReleaseCount = errorReleaseCount;
            this.cancelReleaseCount = cancelReleaseCount;
        }

        public void run() {
            Flux<Void> flux = Flux
                    .range(0, 10)
                    .flatMap(i -> Flux
                            .usingWhen(
                                    Mono.fromSupplier(acquireCount::getAndIncrement).thenReturn("value"),
                                    value -> Mono
                                            .just(value)
                                            .delayElement(Duration.ofMillis(10))
                                            .then(),
                                    value -> Mono.fromSupplier(releaseCount::getAndIncrement).then(),
                                    (value, error) -> Mono.fromSupplier(errorReleaseCount::getAndIncrement).then(),
                                    value -> Mono.fromSupplier(cancelReleaseCount::getAndIncrement).then()
                            )
                            .switchIfEmpty(Mono.error(new RuntimeException("Empty")))
                    )
                    .doOnComplete(() -> cdl.countDown())
                    .doOnError(error -> cdl.countDown());

            try {
                flux.blockLast();
            } catch (Exception e) {
                System.err.println(e);
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
