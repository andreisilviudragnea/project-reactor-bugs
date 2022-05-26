package com.example;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Mono;
import reactor.pool.InstrumentedPool;
import reactor.pool.PoolBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class MissingDrainLoopOnCancelBugTest {

	private final InstrumentedPool<String> stringReactivePool = PoolBuilder
			.from(Mono.just("value").delayElement(Duration.ofMinutes(10)))
			.maxPendingAcquireUnbounded()
			.sizeBetween(0, 1)
			.buildPool();

	@Test
	public void missingDrainLoopOnCancelBug() {
		InstrumentedPool.PoolMetrics metrics = stringReactivePool.metrics();

		AtomicReference<Subscription> subscriptionReference = new AtomicReference<>();

		AtomicBoolean next1 = new AtomicBoolean();

		stringReactivePool.acquire().subscribe(
				stringPooledRef -> next1.set(true),
				throwable -> System.out.println("error1"),
				() -> System.out.println("complete1"),
				subscription -> {
					subscriptionReference.set(subscription);
					subscription.request(Long.MAX_VALUE);
				}
		);

		assertFalse(next1.get());

		AtomicBoolean next2 = new AtomicBoolean();

		stringReactivePool.acquire().subscribe(
				stringPooledRef -> next2.set(true),
				throwable -> System.out.println("error2"),
				() -> System.out.println("complete2")
		);

		assertFalse(next1.get());
		assertFalse(next2.get());

		System.out.println("before cancel");

		subscriptionReference.get().cancel();

		assertFalse(next1.get());
		assertFalse(next2.get()); // should be true

		System.out.println("after cancel");

		assertEquals(0, metrics.acquiredSize());
		assertEquals(1, metrics.pendingAcquireSize()); // should be 0
	}
}
