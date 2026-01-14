package com.vikas.rate_limiter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vikas.rate_limiter.algorithm.TokenBucketRateLimitAlgorithm;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class TokenBucketRaceTest {

    @Test
    void testTokenBucketDoesNotAllowMoreThanMaxRequests() throws Exception {

        int maxCapacity = 20;
        int refillRate = 2;
        int threadsCount = 50;

        TokenBucketRateLimitAlgorithm tokenBucket =
                new TokenBucketRateLimitAlgorithm(refillRate, maxCapacity);
        ExecutorService executor = Executors.newFixedThreadPool(threadsCount);
        CountDownLatch complete = new CountDownLatch(threadsCount);
        CountDownLatch start = new CountDownLatch(1);

        AtomicInteger success = new AtomicInteger();

        for (int i = 0; i < threadsCount; i++) {
            executor.submit(
                    () -> {
                        try {
                            start.await();
                            if (tokenBucket.acceptRequest()) {
                                success.incrementAndGet();
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            complete.countDown();
                        }
                    });
        }

        start.countDown();
        complete.await();
        executor.shutdown();

        assertEquals(success.get(), 20, "Rate-Limiter allowed more requests than tokens...");
    }
}
