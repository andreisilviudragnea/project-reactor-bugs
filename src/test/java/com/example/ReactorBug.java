package com.example;

import org.junit.jupiter.api.RepeatedTest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReactorBug {

    /**
     * https://github.com/reactor/reactor-core/commit/e2ce33ec3a2c72dc25ded3c225b2d82eea854cfd introduced the regression
     * https://github.com/reactor/reactor-core/commit/f47a803087378b30018b969b9c75296b4e549246 fixed the regression
     * 3.2.9.RELEASE is the only affected version
     */
    @RepeatedTest(1_000)
    public void testBug() {
        long dataSize = 2;

        AtomicLong atomicLong = new AtomicLong();
        AtomicLong atomicLong1 = new AtomicLong();
//        AtomicLong atomicLong2 = new AtomicLong();

        List<String> strings = Flux
                .range(0, (int) dataSize)
                .doOnNext(value -> atomicLong1.getAndIncrement())
                .flatMap(i -> {
                    return Mono
                            .empty()
                            .subscribeOn(Schedulers.boundedElastic())
                            .then(Mono.just(""))
//                            .doOnSuccess(value -> atomicLong2.getAndIncrement())
                            ;
                })
                .doOnNext(value -> atomicLong.getAndIncrement())
                .collectList()
                .block();

        assertEquals(dataSize, atomicLong1.get());
        assertEquals(dataSize, atomicLong.get());
//        assertEquals(dataSize, atomicLong2.get());
        assertEquals(dataSize, strings.size());
    }
}
