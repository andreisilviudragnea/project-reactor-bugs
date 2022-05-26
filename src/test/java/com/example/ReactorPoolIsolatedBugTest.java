package com.example;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.pool.InstrumentedPool;
import reactor.pool.PoolBuilder;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReactorPoolIsolatedBugTest {

    private static final int COUNT = 10_000;

    private final InstrumentedPool<String> stringReactivePool = PoolBuilder
            .from(Mono.just("value").delayElement(Duration.ofMillis(2)))
            .maxPendingAcquireUnbounded()
            .evictInBackground(Duration.ofSeconds(1))
            .sizeBetween(0, 3)
            .buildPool();

    @Test
    @Disabled
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
        for (int i = 0; i < COUNT; i++) {
            executorService.submit(new FlatMapErrorTask(cdl));
        }

        cdl.await();
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

    private final class FlatMapErrorTask implements Runnable {

        private final CountDownLatch cdl;

        public FlatMapErrorTask(CountDownLatch cdl) {
            this.cdl = cdl;
        }

        public void run() {
            Flux<Void> flux = Flux
                    .range(0, 10)
                    .flatMap(i -> stringReactivePool
                            .acquire()
                            .delayElement(Duration.ofMillis(100))
                            .flatMap(pooledRef -> Mono
                                    .just(pooledRef.poolable())
                                    .delayElement(Duration.ofMillis(10))
                                    .then(Mono.defer(() -> pooledRef.release()))
                                    .onErrorResume(error -> pooledRef.release())
                                    .doOnCancel(() -> pooledRef.release().subscribe())
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
