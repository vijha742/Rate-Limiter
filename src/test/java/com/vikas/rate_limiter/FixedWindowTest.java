package com.vikas.rate_limiter;

import static org.junit.jupiter.api.Assertions.*;

// public class FixedWindowTest {
//
// @Test
// void shouldAllowRequestsUpToLimitWithinWindow() {
// TestClock clock = new TestClock(Instant.now());
// FixedCounterRateLimitAlgorithm algorithm = new
// FixedCounterRateLimitAlgorithm(5, 10, clock);
//
// for (int i = 0; i < 5; i++) {
// assertTrue(algorithm.acceptRequest(), "Request " + (i + 1) + " should be
// accepted");
// }
//
// assertFalse(algorithm.acceptRequest(), "6th request should be rejected");
// }
//
// @Test
// void shouldRejectRequestsExceedingWindowLimit() {
// TestClock clock = new TestClock(Instant.now());
// FixedCounterRateLimitAlgorithm algorithm = new
// FixedCounterRateLimitAlgorithm(3, 10, clock);
//
// assertTrue(algorithm.acceptRequest());
// assertTrue(algorithm.acceptRequest());
// assertTrue(algorithm.acceptRequest());
// assertFalse(algorithm.acceptRequest(), "Should reject request exceeding
// limit");
// assertFalse(algorithm.acceptRequest(), "Should continue rejecting");
// }
//
// @Test
// void shouldResetCounterWhenWindowExpires() {
// TestClock clock = new TestClock(Instant.now());
// FixedCounterRateLimitAlgorithm algorithm = new
// FixedCounterRateLimitAlgorithm(3, 5, clock);
//
// assertTrue(algorithm.acceptRequest());
// assertTrue(algorithm.acceptRequest());
// assertTrue(algorithm.acceptRequest());
// assertFalse(algorithm.acceptRequest());
//
// clock.advance(Duration.ofSeconds(6));
// assertTrue(algorithm.acceptRequest(), "Should accept after window expires");
// assertEquals(1, algorithm.getCounter(), "Counter should reset to 1");
// }
//
// @Test
// void requestAtWindowBoundaryShouldBelongToNewWindow() {
// TestClock clock = new TestClock(Instant.now());
// FixedCounterRateLimitAlgorithm algorithm = new
// FixedCounterRateLimitAlgorithm(2, 5, clock);
//
// assertTrue(algorithm.acceptRequest());
// assertTrue(algorithm.acceptRequest());
// assertFalse(algorithm.acceptRequest());
//
// clock.advance(Duration.ofSeconds(5).plusMillis(1));
// assertTrue(algorithm.acceptRequest(), "Request at window boundary should be
// in new window");
// }
//
// @Test
// void shouldNotExceedLimitUnderConcurrentRequests() throws
// InterruptedException {
// TestClock clock = new TestClock(Instant.now());
// FixedCounterRateLimitAlgorithm algorithm = new
// FixedCounterRateLimitAlgorithm(10, 10, clock);
//
// int threadCount = 50;
// ExecutorService executor = Executors.newFixedThreadPool(threadCount);
// CountDownLatch startLatch = new CountDownLatch(1);
// CountDownLatch completeLatch = new CountDownLatch(threadCount);
// AtomicInteger acceptedCount = new AtomicInteger(0);
//
// for (int i = 0; i < threadCount; i++) {
// executor.submit(() -> {
// try {
// startLatch.await();
// if (algorithm.acceptRequest()) {
// acceptedCount.incrementAndGet();
// }
// } catch (Exception e) {
// e.printStackTrace();
// } finally {
// completeLatch.countDown();
// }
// });
// }
//
// startLatch.countDown();
// completeLatch.await();
// executor.shutdown();
//
// assertEquals(10, acceptedCount.get(), "Should accept exactly maxRequests
// under concurrent
// load");
// }
//
// @Test
// void concurrentIncrementsShouldBeAtomic() throws InterruptedException {
// TestClock clock = new TestClock(Instant.now());
// FixedCounterRateLimitAlgorithm algorithm = new
// FixedCounterRateLimitAlgorithm(100, 10, clock);
//
// int threadCount = 100;
// ExecutorService executor = Executors.newFixedThreadPool(threadCount);
// CountDownLatch startLatch = new CountDownLatch(1);
// CountDownLatch completeLatch = new CountDownLatch(threadCount);
// AtomicInteger acceptedCount = new AtomicInteger(0);
//
// for (int i = 0; i < threadCount; i++) {
// executor.submit(() -> {
// try {
// startLatch.await();
// if (algorithm.acceptRequest()) {
// acceptedCount.incrementAndGet();
// }
// } catch (Exception e) {
// e.printStackTrace();
// } finally {
// completeLatch.countDown();
// }
// });
// }
//
// startLatch.countDown();
// completeLatch.await();
// executor.shutdown();
//
// assertEquals(100, acceptedCount.get(), "All increments should be atomic");
// assertEquals(100, algorithm.getCounter(), "Counter should match accepted
// count");
// }
//
// @Test
// void shouldAllowBurstAcrossWindowBoundary() {
// TestClock clock = new TestClock(Instant.now());
// FixedCounterRateLimitAlgorithm algorithm = new
// FixedCounterRateLimitAlgorithm(5, 5, clock);
//
// for (int i = 0; i < 5; i++) {
// assertTrue(algorithm.acceptRequest());
// }
// assertFalse(algorithm.acceptRequest());
//
// clock.advance(Duration.ofSeconds(5).plusMillis(1));
//
// for (int i = 0; i < 5; i++) {
// assertTrue(algorithm.acceptRequest(), "Should allow burst after window
// expires");
// }
// }
//
// @Test
// void windowCountsShouldBeIsolatedPerKey() {
// TestClock clock = new TestClock(Instant.now());
// FixedCounterRateLimitAlgorithm algorithm1 = new
// FixedCounterRateLimitAlgorithm(3, 10, clock);
// FixedCounterRateLimitAlgorithm algorithm2 = new
// FixedCounterRateLimitAlgorithm(3, 10, clock);
//
// assertTrue(algorithm1.acceptRequest());
// assertTrue(algorithm1.acceptRequest());
// assertTrue(algorithm1.acceptRequest());
// assertFalse(algorithm1.acceptRequest());
//
// assertTrue(algorithm2.acceptRequest(), "algorithm2 should be independent");
// assertTrue(algorithm2.acceptRequest());
// assertTrue(algorithm2.acceptRequest());
// assertFalse(algorithm2.acceptRequest());
// }
// }
