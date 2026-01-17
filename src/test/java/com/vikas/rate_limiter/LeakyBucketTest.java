package com.vikas.rate_limiter;

import static org.junit.jupiter.api.Assertions.*;

import com.vikas.rate_limiter.algorithm.LeakyBucketRateLimitAlgorithm;
import com.vikas.rate_limiter.utils.TestClock;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class LeakyBucketTest {

	@Test
	void shouldAcceptRequestsUpToQueueCapacity() {
		TestClock clock = new TestClock(Instant.now());
		LeakyBucketRateLimitAlgorithm algorithm = new LeakyBucketRateLimitAlgorithm(2, 10, clock);
		
		for (int i = 0; i < 10; i++) {
			assertTrue(algorithm.acceptRequest(), "Request " + (i + 1) + " should be accepted");
		}
		
		assertEquals(10, algorithm.getCounter());
	}

	@Test
	void shouldRejectRequestsWhenQueueIsFull() {
		TestClock clock = new TestClock(Instant.now());
		LeakyBucketRateLimitAlgorithm algorithm = new LeakyBucketRateLimitAlgorithm(2, 5, clock);
		
		for (int i = 0; i < 5; i++) {
			assertTrue(algorithm.acceptRequest());
		}
		
		assertFalse(algorithm.acceptRequest(), "Should reject when queue is full");
		assertFalse(algorithm.acceptRequest(), "Should continue rejecting");
		assertEquals(5, algorithm.getCounter());
	}

	@Test
	void shouldProcessRequestsAtFixedLeakRate() {
		TestClock clock = new TestClock(Instant.now());
		LeakyBucketRateLimitAlgorithm algorithm = new LeakyBucketRateLimitAlgorithm(5, 20, clock);
		
		for (int i = 0; i < 20; i++) {
			algorithm.acceptRequest();
		}
		
		assertEquals(20, algorithm.getCounter());
		assertFalse(algorithm.acceptRequest());
		
		clock.advance(Duration.ofSeconds(2));
		
		algorithm.acceptRequest();
		assertEquals(11, algorithm.getCounter(), "Should leak 5 requests per second");
	}

	@Test
	void shouldDrainQueueBasedOnElapsedTime() {
		TestClock clock = new TestClock(Instant.now());
		LeakyBucketRateLimitAlgorithm algorithm = new LeakyBucketRateLimitAlgorithm(3, 15, clock);
		
		for (int i = 0; i < 15; i++) {
			algorithm.acceptRequest();
		}
		
		assertEquals(15, algorithm.getCounter());
		
		clock.advance(Duration.ofSeconds(3));
		
		algorithm.acceptRequest();
		assertEquals(7, algorithm.getCounter(), "Should drain 3 requests per second for 3 seconds");
	}

	@Test
	void concurrentProducersShouldNotOverflowQueue() throws InterruptedException {
		TestClock clock = new TestClock(Instant.now());
		LeakyBucketRateLimitAlgorithm algorithm = new LeakyBucketRateLimitAlgorithm(5, 20, clock);
		
		int threadCount = 50;
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch completeLatch = new CountDownLatch(threadCount);
		AtomicInteger acceptedCount = new AtomicInteger(0);
		
		for (int i = 0; i < threadCount; i++) {
			executor.submit(() -> {
				try {
					startLatch.await();
					if (algorithm.acceptRequest()) {
						acceptedCount.incrementAndGet();
					}
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					completeLatch.countDown();
				}
			});
		}
		
		startLatch.countDown();
		completeLatch.await();
		executor.shutdown();
		
		assertEquals(20, acceptedCount.get(), "Should accept exactly max capacity");
		assertEquals(20, algorithm.getCounter());
	}

	@Test
	void producerConsumerInteractionShouldBeThreadSafe() throws InterruptedException {
		TestClock clock = new TestClock(Instant.now());
		LeakyBucketRateLimitAlgorithm algorithm = new LeakyBucketRateLimitAlgorithm(10, 30, clock);
		
		for (int i = 0; i < 30; i++) {
			algorithm.acceptRequest();
		}
		
		clock.advance(Duration.ofSeconds(2));
		
		int threadCount = 40;
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch completeLatch = new CountDownLatch(threadCount);
		AtomicInteger acceptedCount = new AtomicInteger(0);
		
		for (int i = 0; i < threadCount; i++) {
			executor.submit(() -> {
				try {
					startLatch.await();
					if (algorithm.acceptRequest()) {
						acceptedCount.incrementAndGet();
					}
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					completeLatch.countDown();
				}
			});
		}
		
		startLatch.countDown();
		completeLatch.await();
		executor.shutdown();
		
		assertEquals(20, acceptedCount.get(), "Should accept 20 after draining 20 in 2 seconds");
	}

	@Test
	void shouldNotDrainBelowZero() {
		TestClock clock = new TestClock(Instant.now());
		LeakyBucketRateLimitAlgorithm algorithm = new LeakyBucketRateLimitAlgorithm(5, 10, clock);
		
		algorithm.acceptRequest();
		algorithm.acceptRequest();
		
		clock.advance(Duration.ofSeconds(10));
		
		algorithm.acceptRequest();
		assertEquals(1, algorithm.getCounter(), "Counter should not go below zero");
	}

	@Test
	void shouldHandleNoTimeElapsedCorrectly() {
		TestClock clock = new TestClock(Instant.now());
		LeakyBucketRateLimitAlgorithm algorithm = new LeakyBucketRateLimitAlgorithm(5, 10, clock);
		
		for (int i = 0; i < 10; i++) {
			algorithm.acceptRequest();
		}
		
		assertFalse(algorithm.acceptRequest());
		assertEquals(10, algorithm.getCounter());
	}

	@Test
	void shouldAcceptRequestsAfterCompleteDrain() {
		TestClock clock = new TestClock(Instant.now());
		LeakyBucketRateLimitAlgorithm algorithm = new LeakyBucketRateLimitAlgorithm(5, 10, clock);
		
		for (int i = 0; i < 10; i++) {
			algorithm.acceptRequest();
		}
		
		assertFalse(algorithm.acceptRequest());
		
		clock.advance(Duration.ofSeconds(3));
		
		for (int i = 0; i < 10; i++) {
			assertTrue(algorithm.acceptRequest(), "Should accept after complete drain");
		}
		
		assertFalse(algorithm.acceptRequest());
	}

	@Test
	void shouldHandleLargeTimeJump() {
		TestClock clock = new TestClock(Instant.now());
		LeakyBucketRateLimitAlgorithm algorithm = new LeakyBucketRateLimitAlgorithm(10, 50, clock);
		
		for (int i = 0; i < 50; i++) {
			algorithm.acceptRequest();
		}
		
		clock.advance(Duration.ofHours(1));
		
		algorithm.acceptRequest();
		assertEquals(1, algorithm.getCounter(), "Should drain completely after large time jump");
	}

	@Test
	void concurrentDrainAndAddShouldBeConsistent() throws InterruptedException {
		TestClock clock = new TestClock(Instant.now());
		LeakyBucketRateLimitAlgorithm algorithm = new LeakyBucketRateLimitAlgorithm(5, 25, clock);
		
		for (int i = 0; i < 25; i++) {
			algorithm.acceptRequest();
		}
		
		clock.advance(Duration.ofSeconds(3));
		
		int threadCount = 50;
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch completeLatch = new CountDownLatch(threadCount);
		AtomicInteger successCount = new AtomicInteger(0);
		
		for (int i = 0; i < threadCount; i++) {
			executor.submit(() -> {
				try {
					startLatch.await();
					if (algorithm.acceptRequest()) {
						successCount.incrementAndGet();
					}
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					completeLatch.countDown();
				}
			});
		}
		
		startLatch.countDown();
		completeLatch.await();
		executor.shutdown();
		
		assertTrue(successCount.get() <= 15, "Should drain 15 (5*3) and allow 15 more");
	}

	@Test
	void shouldMaintainCounterAccuracyWithPartialDrain() {
		TestClock clock = new TestClock(Instant.now());
		LeakyBucketRateLimitAlgorithm algorithm = new LeakyBucketRateLimitAlgorithm(3, 20, clock);
		
		for (int i = 0; i < 15; i++) {
			algorithm.acceptRequest();
		}
		
		clock.advance(Duration.ofSeconds(2));
		
		algorithm.acceptRequest();
		assertEquals(10, algorithm.getCounter(), "Counter should be 15 - 6 + 1 = 10");
	}
}
