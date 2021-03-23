package com.example;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class MonoUsingWhenBugTest {

    private static final int COUNT = 10_000;

    @Test
    public void monoUsingWhenBug() throws InterruptedException {
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

        loggerThreads.submit(new Logger(acquireCount, acquireCount1, releaseCount, errorReleaseCount, cancelReleaseCount, releaseCount1, errorReleaseCount1, cancelReleaseCount1));

        ExecutorService executorService = Executors.newFixedThreadPool(
                16,
                r -> {
                    Thread t = Executors.defaultThreadFactory().newThread(r);
                    t.setDaemon(true);
                    return t;
                }
        );

        CountDownLatch cdl = new CountDownLatch(COUNT);

        Semaphore pool = new Semaphore(3);

        for (int i = 0; i < COUNT; i++) {
            executorService.submit(new FlatMapErrorTask(pool, cdl, acquireCount, acquireCount1, releaseCount, errorReleaseCount, cancelReleaseCount, releaseCount1, errorReleaseCount1, cancelReleaseCount1));
        }

        cdl.await();
    }

    private static final class Logger implements Runnable {

        private final AtomicInteger acquireCount;
        private final AtomicInteger acquireCount1;
        private final AtomicInteger releaseCount;
        private final AtomicInteger errorReleaseCount;
        private final AtomicInteger cancelReleaseCount;
        private final AtomicInteger releaseCount1;
        private final AtomicInteger errorReleaseCount1;
        private final AtomicInteger cancelReleaseCount1;

        private Logger(AtomicInteger acquireCount, AtomicInteger acquireCount1, AtomicInteger releaseCount, AtomicInteger errorReleaseCount, AtomicInteger cancelReleaseCount, AtomicInteger releaseCount1, AtomicInteger errorReleaseCount1, AtomicInteger cancelReleaseCount1) {
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
                        "[Metrics] AcquireCount = %d %d ReleaseCount = %d %d ErrorReleaseCount = %d %d CancelReleaseCount = %d %d TotalReleaseCount = %d %d%n",
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

        private final Semaphore pool;
        private final CountDownLatch cdl;
        private final AtomicInteger acquireCount;
        private final AtomicInteger acquireCount1;
        private final AtomicInteger releaseCount;
        private final AtomicInteger errorReleaseCount;
        private final AtomicInteger cancelReleaseCount;
        private final AtomicInteger releaseCount1;
        private final AtomicInteger errorReleaseCount1;
        private final AtomicInteger cancelReleaseCount1;

        public FlatMapErrorTask(Semaphore pool, CountDownLatch cdl, AtomicInteger acquireCount, AtomicInteger acquireCount1, AtomicInteger releaseCount, AtomicInteger errorReleaseCount, AtomicInteger cancelReleaseCount, AtomicInteger releaseCount1, AtomicInteger errorReleaseCount1, AtomicInteger cancelReleaseCount1) {
            this.pool = pool;
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
                                return Mono
                                        .fromSupplier(() -> {
                                            try {
                                                pool.acquire();
                                            } catch (InterruptedException e) {
                                                throw new IllegalStateException(e.getMessage(), e);
                                            }
                                            return "value";
                                        })
                                        .subscribeOn(Schedulers.boundedElastic())
                                        .doOnSuccess(unused -> acquireCount1.getAndIncrement())
//                                        .doOnCancel(() -> {
//                                            cancelReleaseCount.getAndIncrement();
//                                            Mono.fromSupplier(() -> {
//                                                pool.release();
//                                                return "value";
//                                            }).doOnSuccess(unused -> cancelReleaseCount1.getAndIncrement()).subscribe();
//                                        })
                                        ;
                            }),
                            value -> Mono
                                    .just(value)
                                    .delayElement(Duration.ofMillis(10))
                                    .then(),
                            value -> {
                                releaseCount.getAndIncrement();
                                return Mono.fromSupplier(() -> {
                                    pool.release();
                                    return "value";
                                }).doOnSuccess(unused -> releaseCount1.getAndIncrement()).then();
                            },
                            (value, error) -> {
                                errorReleaseCount.getAndIncrement();
                                return Mono.fromSupplier(() -> {
                                    pool.release();
                                    return "value";
                                }).doOnSuccess(unused -> errorReleaseCount1.getAndIncrement()).then();
                            },
                            value -> {
                                cancelReleaseCount.getAndIncrement();
                                return Mono.fromSupplier(() -> {
                                    pool.release();
                                    return "value";
                                }).doOnSuccess(unused -> cancelReleaseCount1.getAndIncrement()).then();
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
