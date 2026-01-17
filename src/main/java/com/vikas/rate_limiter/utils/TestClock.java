package com.vikas.rate_limiter.utils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

public class TestClock extends Clock {

    private Instant current;

    public TestClock(Instant start) {
        this.current = start;
    }

    public void advance(Duration duration) {
        current = current.plus(duration);
    }
    
    public void setInstant(Instant instant) {
        this.current = instant;
    }

    @Override
    public Instant instant() {
        return current;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
