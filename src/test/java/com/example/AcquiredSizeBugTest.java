package com.example;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Mono;
import reactor.pool.InstrumentedPool;
import reactor.pool.PoolBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class AcquiredSizeBugTest {

	@Test
	public void acquiredSizeBug() {
		InstrumentedPool<String> stringReactivePool = PoolBuilder
				.from(Mono.just("value").delayElement(Duration.ofMinutes(10)))
				.maxPendingAcquireUnbounded()
				.sizeBetween(0, 1)
				.buildPool();

		AtomicBoolean next = new AtomicBoolean();
		AtomicBoolean error = new AtomicBoolean();
		AtomicBoolean complete = new AtomicBoolean();

		stringReactivePool.acquire().subscribe(
				stringPooledRef -> next.set(true),
				throwable -> error.set(true),
				() -> complete.set(true),
				(Consumer<? super Subscription>) null
		);

		assertFalse(next.get());
		assertFalse(error.get());
		assertFalse(complete.get());

		assertEquals(0, stringReactivePool.metrics().acquiredSize());
	}
}
