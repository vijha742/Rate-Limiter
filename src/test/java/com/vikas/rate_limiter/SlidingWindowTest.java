package com.vikas.rate_limiter;

import static org.junit.jupiter.api.Assertions.*;

// @Slf4j
// public class SlidingWindowTest {
//
// @Test
// void shouldAllowRequestsWithinSlidingWindowLimit() {
// TestClock clock = new TestClock(Instant.now());
// SlidingWindowRateLimitAlgorithm algorithm = new
// SlidingWindowRateLimitAlgorithm(clock, 5, 10);
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
// void shouldRejectRequestsExceedingSlidingWindowLimit() {
// TestClock clock = new TestClock(Instant.now());
// SlidingWindowRateLimitAlgorithm algorithm = new
// SlidingWindowRateLimitAlgorithm(clock, 3, 10);
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
// void expiredRequestsShouldBeEvictedFromWindow() {
// TestClock clock = new TestClock(Instant.now());
// SlidingWindowRateLimitAlgorithm algorithm = new
// SlidingWindowRateLimitAlgorithm(clock, 3, 5);
//
// assertTrue(algorithm.acceptRequest());
// assertTrue(algorithm.acceptRequest());
// assertTrue(algorithm.acceptRequest());
// assertFalse(algorithm.acceptRequest());
//
// int initialSize = algorithm.getReqStorage().size();
//
// clock.advance(Duration.ofSeconds(11));
// assertTrue(algorithm.acceptRequest(), "Should accept after old requests
// expire");
//
// assertTrue(algorithm.getReqStorage().size() < initialSize, "Old entries
// should be evicted");
// }
//
// @Test
// void requestCountShouldDecreaseAsWindowSlides() {
// TestClock clock = new TestClock(Instant.now());
// SlidingWindowRateLimitAlgorithm algorithm = new
// SlidingWindowRateLimitAlgorithm(clock, 3, 5);
//
// assertTrue(algorithm.acceptRequest());
// clock.advance(Duration.ofSeconds(1));
// assertTrue(algorithm.acceptRequest());
// clock.advance(Duration.ofSeconds(1));
// assertTrue(algorithm.acceptRequest());
// assertFalse(algorithm.acceptRequest());
//
// clock.advance(Duration.ofSeconds(4));
// assertTrue(algorithm.acceptRequest(), "Oldest request should have slid out of
// window");
// }
//
// @Test
// void shouldCountOnlyRequestsWithinTimeRange() {
// TestClock clock = new TestClock(Instant.now());
// SlidingWindowRateLimitAlgorithm algorithm = new
// SlidingWindowRateLimitAlgorithm(clock, 5, 10);
//
// assertTrue(algorithm.acceptRequest());
// assertTrue(algorithm.acceptRequest());
//
// clock.advance(Duration.ofSeconds(11));
//
// for (int i = 0; i < 5; i++) {
// assertTrue(algorithm.acceptRequest(), "Old requests should not count");
// }
// assertFalse(algorithm.acceptRequest());
// }
//
// @Test
// void shouldNotCountExpiredRequests() {
// TestClock clock = new TestClock(Instant.now());
// SlidingWindowRateLimitAlgorithm algorithm = new
// SlidingWindowRateLimitAlgorithm(clock, 2, 5);
//
// assertTrue(algorithm.acceptRequest());
// assertTrue(algorithm.acceptRequest());
// assertFalse(algorithm.acceptRequest());
//
// clock.advance(Duration.ofSeconds(6));
//
// assertTrue(algorithm.acceptRequest(), "Should not count expired requests");
// assertTrue(algorithm.acceptRequest());
// assertFalse(algorithm.acceptRequest());
// }
//
// @Test
// void concurrentRequestsShouldNotOverCount() throws InterruptedException {
// TestClock clock = new TestClock(Instant.now());
// SlidingWindowRateLimitAlgorithm algorithm = new
// SlidingWindowRateLimitAlgorithm(clock, 10, 10);
//
// int threadCount = 50;
// ExecutorService executor = Executors.newFixedThreadPool(threadCount);
// CountDownLatch startLatch = new CountDownLatch(1);
// CountDownLatch completeLatch = new CountDownLatch(threadCount);
// AtomicInteger acceptedCount = new AtomicInteger(0);
//
// for (int i = 0; i < threadCount; i++) {
// executor.submit(
// () -> {
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
// assertEquals(
// 10, acceptedCount.get(), "Should accept exactly maxRequests under concurrent
// load");
// }
//
// @Test
// void concurrentEvictionAndAddShouldBeThreadSafe() throws InterruptedException
// {
// TestClock clock = new TestClock(Instant.now());
// SlidingWindowRateLimitAlgorithm algorithm = new
// SlidingWindowRateLimitAlgorithm(clock, 20, 5);
//
// for (int i = 0; i < 10; i++) {
// algorithm.acceptRequest();
// }
//
// clock.advance(Duration.ofSeconds(6));
//
// int threadCount = 30;
// ExecutorService executor = Executors.newFixedThreadPool(threadCount);
// CountDownLatch startLatch = new CountDownLatch(1);
// CountDownLatch completeLatch = new CountDownLatch(threadCount);
// AtomicInteger acceptedCount = new AtomicInteger(0);
//
// for (int i = 0; i < threadCount; i++) {
// executor.submit(
// () -> {
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
// assertEquals(20, acceptedCount.get(), "Concurrent eviction and add should be
// thread-safe");
// }
//
// @Test
// void shouldNotGrowWindowUnboundedUnderHighLoad() {
// TestClock clock = new TestClock(Instant.now());
// SlidingWindowRateLimitAlgorithm algorithm = new
// SlidingWindowRateLimitAlgorithm(clock, 100,
// 10);
//
// for (int i = 0; i < 100; i++) {
// algorithm.acceptRequest();
// }
//
// int sizeAfter100 = algorithm.getReqStorage().size();
//
// for (int i = 0; i < 100; i++) {
// algorithm.acceptRequest();
// }
//
// assertTrue(
// algorithm.getReqStorage().size() <= sizeAfter100 + 10,
// "Storage should not grow unbounded");
// }
//
// @Test
// void shouldHandleRequestsAtSameTimestamp() {
// TestClock clock = new TestClock(Instant.now());
// SlidingWindowRateLimitAlgorithm algorithm = new
// SlidingWindowRateLimitAlgorithm(clock, 5, 10);
//
// for (int i = 0; i < 5; i++) {
// assertTrue(
// algorithm.acceptRequest(), "Should handle multiple requests at same
// timestamp");
// }
//
// assertFalse(algorithm.acceptRequest());
// }
//
// @Test
// void shouldAccuratelyTrackMultipleRequestsPerTimestamp() {
// TestClock clock = new TestClock(Instant.now());
// SlidingWindowRateLimitAlgorithm algorithm = new
// SlidingWindowRateLimitAlgorithm(clock, 10, 5);
//
// assertTrue(algorithm.acceptRequest());
// assertTrue(algorithm.acceptRequest());
// assertTrue(algorithm.acceptRequest());
//
// assertEquals(3, algorithm.getReqStorage().size(), "Should track each request
// separately");
// }
// }
