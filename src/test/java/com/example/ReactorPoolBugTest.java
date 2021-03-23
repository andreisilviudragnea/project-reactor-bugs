package com.example;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.pool.InstrumentedPool;
import reactor.pool.PoolBuilder;

public class ReactorPoolBugTest {

    private static final int COUNT = 10_000;

    private final InstrumentedPool<String> stringReactivePool = PoolBuilder
            .from(Mono.just("value").delayElement(Duration.ofMillis(2)))
            .maxPendingAcquireUnbounded()
            .evictInBackground(Duration.ofSeconds(1))
            .sizeBetween(0, 3)
            .buildPool();

    @Test
    public void reactorPoolBug() throws InterruptedException {
        AtomicInteger acquireCount = new AtomicInteger();
        AtomicInteger acquireCount1 = new AtomicInteger();
        AtomicInteger releaseCount = new AtomicInteger();
        AtomicInteger errorReleaseCount = new AtomicInteger();
        AtomicInteger cancelReleaseCount = new AtomicInteger();
        AtomicInteger releaseCount1 = new AtomicInteger();
        AtomicInteger errorReleaseCount1 = new AtomicInteger();
        AtomicInteger cancelReleaseCount1 = new AtomicInteger();

        ExecutorService loggerThreads = Executors.newFixedThreadPool(
                1,
                r -> {
                    Thread t = Executors.defaultThreadFactory().newThread(r);
                    t.setDaemon(true);
                    return t;
                }
        );

        loggerThreads.submit(new PoolMetricsLogger(stringReactivePool.metrics(), acquireCount, acquireCount1, releaseCount, errorReleaseCount, cancelReleaseCount, releaseCount1, errorReleaseCount1, cancelReleaseCount1));

        ExecutorService executorService = Executors.newFixedThreadPool(
                16,
                r -> {
                    Thread t = Executors.defaultThreadFactory().newThread(r);
                    t.setDaemon(true);
                    return t;
                }
        );

        CountDownLatch cdl = new CountDownLatch(COUNT);

        for (int i = 0; i < COUNT; i++) {
            executorService.submit(new FlatMapErrorTask(cdl, acquireCount, acquireCount1, releaseCount, errorReleaseCount, cancelReleaseCount, releaseCount1, errorReleaseCount1, cancelReleaseCount1));
        }

        cdl.await();
    }

    private static final class PoolMetricsLogger implements Runnable {

        private final InstrumentedPool.PoolMetrics poolMetrics;
        private final AtomicInteger acquireCount;
        private final AtomicInteger acquireCount1;
        private final AtomicInteger releaseCount;
        private final AtomicInteger errorReleaseCount;
        private final AtomicInteger cancelReleaseCount;
        private final AtomicInteger releaseCount1;
        private final AtomicInteger errorReleaseCount1;
        private final AtomicInteger cancelReleaseCount1;

        private PoolMetricsLogger(InstrumentedPool.PoolMetrics poolMetrics, AtomicInteger acquireCount, AtomicInteger acquireCount1, AtomicInteger releaseCount, AtomicInteger errorReleaseCount, AtomicInteger cancelReleaseCount, AtomicInteger releaseCount1, AtomicInteger errorReleaseCount1, AtomicInteger cancelReleaseCount1) {
            this.poolMetrics = poolMetrics;
            this.acquireCount = acquireCount;
            this.acquireCount1 = acquireCount1;
            this.releaseCount = releaseCount;
            this.errorReleaseCount = errorReleaseCount;
            this.cancelReleaseCount = cancelReleaseCount;
            this.releaseCount1 = releaseCount1;
            this.errorReleaseCount1 = errorReleaseCount1;
            this.cancelReleaseCount1 = cancelReleaseCount1;
        }

        public void run() {
            while (true) {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                System.err.printf(
                        "[POOL Metrics] Acquired = %d Pending = %d Idle = %d AcquireCount = %d %d ReleaseCount = %d %d ErrorReleaseCount = %d %d CancelReleaseCount = %d %d TotalReleaseCount = %d %d%n",
                        poolMetrics.acquiredSize(),
                        poolMetrics.pendingAcquireSize(),
                        poolMetrics.idleSize(),
                        acquireCount.get(),
                        acquireCount1.get(),
                        releaseCount.get(),
                        releaseCount1.get(),
                        errorReleaseCount.get(),
                        errorReleaseCount1.get(),
                        cancelReleaseCount.get(),
                        cancelReleaseCount1.get(),
                        releaseCount.get() + errorReleaseCount.get() + cancelReleaseCount.get(),
                        releaseCount1.get() + errorReleaseCount1.get() + cancelReleaseCount1.get()
                );
            }
        }
    }

    private final class FlatMapErrorTask implements Runnable {

        private final CountDownLatch cdl;
        private final AtomicInteger acquireCount;
        private final AtomicInteger acquireCount1;
        private final AtomicInteger releaseCount;
        private final AtomicInteger errorReleaseCount;
        private final AtomicInteger cancelReleaseCount;
        private final AtomicInteger releaseCount1;
        private final AtomicInteger errorReleaseCount1;
        private final AtomicInteger cancelReleaseCount1;

        public FlatMapErrorTask(CountDownLatch cdl, AtomicInteger acquireCount, AtomicInteger acquireCount1, AtomicInteger releaseCount, AtomicInteger errorReleaseCount, AtomicInteger cancelReleaseCount, AtomicInteger releaseCount1, AtomicInteger errorReleaseCount1, AtomicInteger cancelReleaseCount1) {
            this.cdl = cdl;
            this.acquireCount = acquireCount;
            this.acquireCount1 = acquireCount1;
            this.releaseCount = releaseCount;
            this.errorReleaseCount = errorReleaseCount;
            this.cancelReleaseCount = cancelReleaseCount;
            this.releaseCount1 = releaseCount1;
            this.errorReleaseCount1 = errorReleaseCount1;
            this.cancelReleaseCount1 = cancelReleaseCount1;
        }

        public void run() {
            Flux<Void> flux = Flux
                    .range(0, 10)
                    .flatMap(i -> Mono.usingWhen(
                            Mono.defer(() -> {
                                acquireCount.getAndIncrement();
                                return stringReactivePool
                                        .acquire()
                                        .doOnSuccess(unused -> acquireCount1.getAndIncrement())
                                        .delayElement(Duration.ofMillis(100))
                                        .flatMap(stringPooledRef -> Mono.just(stringPooledRef).doOnCancel(() -> {
                                            cancelReleaseCount.getAndIncrement();
                                            stringPooledRef.release().doOnSuccess(unused -> cancelReleaseCount1.getAndIncrement()).subscribe();
                                        }));
                            }),
                            slot -> Mono
                                    .just(slot.poolable())
                                    .delayElement(Duration.ofMillis(10))
                                    .then(),
                            stringPooledRef -> {
                                releaseCount.getAndIncrement();
                                return stringPooledRef.release().doOnSuccess(unused -> releaseCount1.getAndIncrement());
                            },
                            (ref, error) -> {
                                errorReleaseCount.getAndIncrement();
                                return ref.release().doOnSuccess(unused -> errorReleaseCount1.getAndIncrement());
                            },
                            stringPooledRef1 -> {
                                cancelReleaseCount.getAndIncrement();
                                return stringPooledRef1.release().doOnSuccess(unused -> cancelReleaseCount1.getAndIncrement());
                            }
                            ).switchIfEmpty(Mono.error(new RuntimeException("Empty")))
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
