package com.codesync.backend.tutor;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TutorRateLimiterTest {

    private static final class MovableClock extends Clock {
        Instant now = Instant.parse("2026-10-25T10:00:00Z");
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void hourlyLimitRefillsAfterAnHour() {
        MovableClock clock = new MovableClock();
        TutorRateLimiter limiter = new TutorRateLimiter(3, 10, 100, clock);
        assertEquals(new TutorRateLimiter.Allowance(2, 9), limiter.take("ana"));
        limiter.take("ana");
        limiter.take("ana");
        TutorRateLimiter.LimitReachedException refused =
                assertThrows(TutorRateLimiter.LimitReachedException.class, () -> limiter.take("ana"));
        assertTrue(refused.getMessage().contains("about 60 minutes"), refused.getMessage());

        // Someone else is not affected
        limiter.take("ben");

        clock.now = clock.now.plus(Duration.ofMinutes(61));
        assertEquals(new TutorRateLimiter.Allowance(2, 6), limiter.take("ana"));
    }

    @Test
    void dailyLimitAndServerLimit() {
        MovableClock clock = new MovableClock();
        TutorRateLimiter limiter = new TutorRateLimiter(100, 2, 3, clock);
        limiter.take("ana");
        limiter.take("ana");
        assertTrue(assertThrows(TutorRateLimiter.LimitReachedException.class, () -> limiter.take("ana"))
                .getMessage().contains("tomorrow"));
        limiter.take("ben");
        assertTrue(assertThrows(TutorRateLimiter.LimitReachedException.class, () -> limiter.take("cy"))
                .getMessage().contains("as many questions as it can today"));

        clock.now = clock.now.plus(Duration.ofHours(25));
        limiter.take("ana");
        limiter.take("cy");
    }

    @Test
    void aFailedAnswerIsGivenBack() {
        TutorRateLimiter limiter = new TutorRateLimiter(1, 5, 5, new MovableClock());
        limiter.take("ana");
        limiter.giveBack("ana");
        assertEquals(new TutorRateLimiter.Allowance(1, 5), limiter.peek("ana"));
        limiter.take("ana");
    }
}
