package com.vikas.rate_limiter;

// public class TokenBucketTest {
//
// @Test
// void testTokenBucketDoesNotAllowMoreThanMaxRequests() throws Exception {
//
// int maxCapacity = 20;
// int refillRate = 2;
// int threadsCount = 50;
//
// TestClock clock = new TestClock(Instant.now());
//
// TokenBucketRateLimitAlgorithm tokenBucket = new
// TokenBucketRateLimitAlgorithm(refillRate,
// maxCapacity, clock);
// ExecutorService executor = Executors.newFixedThreadPool(threadsCount);
// CountDownLatch complete = new CountDownLatch(threadsCount);
// CountDownLatch start = new CountDownLatch(1);
//
// AtomicInteger success = new AtomicInteger();
//
// for (int i = 0; i < threadsCount; i++) {
// executor.submit(
// () -> {
// try {
// start.await();
// if (tokenBucket.acceptRequest()) {
// success.incrementAndGet();
// }
//
// } catch (Exception e) {
// e.printStackTrace();
// } finally {
// complete.countDown();
// }
// });
// }
//
// start.countDown();
// complete.await();
// executor.shutdown();
//
// assertEquals(success.get(), 20, "Rate-Limiter allowed more requests than
// tokens...");
// }
//
// @Test
// void oneRequestConsumesOnlyOneToken() {
// int maxCapacity = 20;
// int refillRate = 2;
// int iterations = 50;
//
// TokenBucketRateLimitAlgorithm tokenBucket = new
// TokenBucketRateLimitAlgorithm(
// refillRate, maxCapacity, Clock.systemDefaultZone());
//
// int success = 0;
//
// for (int i = 0; i < iterations; i++) {
// if (tokenBucket.acceptRequest())
// success++;
// }
//
// assertEquals(20, success, "Algorithm is consuming more tokens than
// requests...");
// }
//
// @Test
// void shouldRejectRequestWhenBucketIsEmpty() {
// TestClock clock = new TestClock(Instant.now());
// TokenBucketRateLimitAlgorithm tokenBucket = new
// TokenBucketRateLimitAlgorithm(2, 5,
// clock);
//
// for (int i = 0; i < 5; i++) {
// assertTrue(tokenBucket.acceptRequest());
// }
//
// assertFalse(tokenBucket.acceptRequest(), "Should reject when bucket is
// empty");
// assertEquals(0, tokenBucket.getTokensInBucket());
// }
//
// @Test
// void shouldNotExceedCapacityAfterRefill() {
// TestClock clock = new TestClock(Instant.now());
// TokenBucketRateLimitAlgorithm tokenBucket = new
// TokenBucketRateLimitAlgorithm(5, 10,
// clock);
//
// for (int i = 0; i < 10; i++) {
// tokenBucket.acceptRequest();
// }
//
// clock.advance(Duration.ofSeconds(10));
//
// tokenBucket.acceptRequest();
//
// assertEquals(
// 9,
// tokenBucket.getTokensInBucket(),
// "Tokens should not exceed capacity after refill");
// }
//
// @Test
// void shouldRefillTokensBasedOnElapsedTime() {
// TestClock clock = new TestClock(Instant.now());
// TokenBucketRateLimitAlgorithm tokenBucket = new
// TokenBucketRateLimitAlgorithm(5, 20,
// clock);
//
// for (int i = 0; i < 20; i++) {
// tokenBucket.acceptRequest();
// }
//
// assertFalse(tokenBucket.acceptRequest());
//
// clock.advance(Duration.ofSeconds(2));
//
// for (int i = 0; i < 10; i++) {
// assertTrue(tokenBucket.acceptRequest(), "Should refill 5 tokens per second");
// }
//
// assertFalse(tokenBucket.acceptRequest());
// }
//
// @Test
// void eachAllowedRequestShouldConsumeExactlyOneToken() {
// TestClock clock = new TestClock(Instant.now());
// TokenBucketRateLimitAlgorithm tokenBucket = new
// TokenBucketRateLimitAlgorithm(2, 10,
// clock);
//
// assertTrue(tokenBucket.acceptRequest());
// assertEquals(9, tokenBucket.getTokensInBucket());
//
// assertTrue(tokenBucket.acceptRequest());
// assertEquals(8, tokenBucket.getTokensInBucket());
//
// assertTrue(tokenBucket.acceptRequest());
// assertEquals(7, tokenBucket.getTokensInBucket());
// }
//
// @Test
// void shouldNotConsumeTokenWhenRequestIsRejected() {
// TestClock clock = new TestClock(Instant.now());
// TokenBucketRateLimitAlgorithm tokenBucket = new
// TokenBucketRateLimitAlgorithm(2, 3,
// clock);
//
// tokenBucket.acceptRequest();
// tokenBucket.acceptRequest();
// tokenBucket.acceptRequest();
//
// assertFalse(tokenBucket.acceptRequest());
// assertEquals(0, tokenBucket.getTokensInBucket(), "Should not consume token on
// rejection");
// }
//
// @Test
// void shouldNotExceedCapacityUnderConcurrentRequests() throws
// InterruptedException {
// TestClock clock = new TestClock(Instant.now());
// TokenBucketRateLimitAlgorithm tokenBucket = new
// TokenBucketRateLimitAlgorithm(5, 15,
// clock);
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
// if (tokenBucket.acceptRequest()) {
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
// assertEquals(15, acceptedCount.get(), "Should not exceed capacity under
// concurrent
// load");
// }
//
// @Test
// void shouldConsumeTokensAtomicallyUnderConcurrency() throws
// InterruptedException {
// TestClock clock = new TestClock(Instant.now());
// TokenBucketRateLimitAlgorithm tokenBucket = new
// TokenBucketRateLimitAlgorithm(2, 25,
// clock);
//
// int threadCount = 100;
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
// if (tokenBucket.acceptRequest()) {
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
// assertEquals(25, acceptedCount.get(), "Token consumption should be atomic");
// assertEquals(0, tokenBucket.getTokensInBucket());
// }
//
// @Test
// void concurrentConsumeShouldNotDuplicateTokens() throws InterruptedException
// {
// TestClock clock = new TestClock(Instant.now());
// TokenBucketRateLimitAlgorithm tokenBucket = new
// TokenBucketRateLimitAlgorithm(1, 10,
// clock);
//
// int threadCount = 20;
// ExecutorService executor = Executors.newFixedThreadPool(threadCount);
// CountDownLatch startLatch = new CountDownLatch(1);
// CountDownLatch completeLatch = new CountDownLatch(threadCount);
// AtomicInteger successCount = new AtomicInteger(0);
// AtomicInteger failureCount = new AtomicInteger(0);
//
// for (int i = 0; i < threadCount; i++) {
// executor.submit(
// () -> {
// try {
// startLatch.await();
// if (tokenBucket.acceptRequest()) {
// successCount.incrementAndGet();
// } else {
// failureCount.incrementAndGet();
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
// assertEquals(10, successCount.get());
// assertEquals(10, failureCount.get());
// }
//
// @Test
// void concurrentRefillAndConsumeShouldNotCreateExtraTokens() throws
// InterruptedException {
// TestClock clock = new TestClock(Instant.now());
// TokenBucketRateLimitAlgorithm tokenBucket = new
// TokenBucketRateLimitAlgorithm(10, 20,
// clock);
//
// for (int i = 0; i < 20; i++) {
// tokenBucket.acceptRequest();
// }
//
// clock.advance(Duration.ofSeconds(1));
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
// if (tokenBucket.acceptRequest()) {
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
// assertEquals(10, acceptedCount.get(), "Should refill exactly 10 tokens in 1
// second");
// }
//
// @Test
// void concurrentRequestsAfterRefillShouldRespectCapacity() throws
// InterruptedException {
// TestClock clock = new TestClock(Instant.now());
// TokenBucketRateLimitAlgorithm tokenBucket = new
// TokenBucketRateLimitAlgorithm(5, 10,
// clock);
//
// for (int i = 0; i < 10; i++) {
// tokenBucket.acceptRequest();
// }
//
// clock.advance(Duration.ofSeconds(5));
//
// int threadCount = 40;
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
// if (tokenBucket.acceptRequest()) {
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
// assertTrue(acceptedCount.get() <= 10, "Should not exceed max capacity after
// refill");
// }
//
// @Test
// void shouldNotRefillWhenNoTimeHasElapsed() {
// TestClock clock = new TestClock(Instant.now());
// TokenBucketRateLimitAlgorithm tokenBucket = new
// TokenBucketRateLimitAlgorithm(10, 15,
// clock);
//
// for (int i = 0; i < 15; i++) {
// tokenBucket.acceptRequest();
// }
//
// assertFalse(tokenBucket.acceptRequest());
// assertFalse(tokenBucket.acceptRequest());
// assertEquals(0, tokenBucket.getTokensInBucket());
// }
//
// @Test
// void shouldHandleLargeTimeJumpWithoutOverflow() {
// TestClock clock = new TestClock(Instant.now());
// TokenBucketRateLimitAlgorithm tokenBucket = new
// TokenBucketRateLimitAlgorithm(100, 50,
// clock);
//
// for (int i = 0; i < 50; i++) {
// tokenBucket.acceptRequest();
// }
//
// clock.advance(Duration.ofHours(24));
//
// assertTrue(tokenBucket.acceptRequest());
// assertEquals(49, tokenBucket.getTokensInBucket(), "Should cap at max
// capacity");
// }
//
// @Test
// void shouldHandleZeroRefillRateCorrectly() {
// TestClock clock = new TestClock(Instant.now());
// TokenBucketRateLimitAlgorithm tokenBucket = new
// TokenBucketRateLimitAlgorithm(0, 5,
// clock);
//
// for (int i = 0; i < 5; i++) {
// assertTrue(tokenBucket.acceptRequest());
// }
//
// assertFalse(tokenBucket.acceptRequest());
//
// clock.advance(Duration.ofSeconds(10));
//
// assertFalse(tokenBucket.acceptRequest(), "Should not refill with zero rate");
// }
//
// @Test
// void shouldMaintainCorrectTokenCountAfterPartialConsumption() {
// TestClock clock = new TestClock(Instant.now());
// TokenBucketRateLimitAlgorithm tokenBucket = new
// TokenBucketRateLimitAlgorithm(5, 20,
// clock);
//
// for (int i = 0; i < 10; i++) {
// tokenBucket.acceptRequest();
// }
//
// assertEquals(10, tokenBucket.getTokensInBucket());
//
// clock.advance(Duration.ofSeconds(1));
//
// tokenBucket.acceptRequest();
// assertEquals(14, tokenBucket.getTokensInBucket());
// }
// }
