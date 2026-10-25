package com.codesync.backend.tutor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * How many hints each person may ask for, per hour and per day, plus a cap for the whole server
 * per day so one free API key lasts. In memory: a restart forgets the counts, which only errs
 * on the generous side.
 */
public class TutorRateLimiter {

    public record Allowance(int leftThisHour, int leftToday) {
    }

    public static class LimitReachedException extends RuntimeException {
        public LimitReachedException(String message) {
            super(message);
        }
    }

    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofDays(1);

    private final int perHour;
    private final int perDay;
    private final int serverPerDay;
    private final Clock clock;
    private final Map<String, Deque<Instant>> byUser = new HashMap<>();
    private final Deque<Instant> server = new ArrayDeque<>();

    public TutorRateLimiter(int perHour, int perDay, int serverPerDay, Clock clock) {
        this.perHour = perHour;
        this.perDay = perDay;
        this.serverPerDay = serverPerDay;
        this.clock = clock;
    }

    /** Counts one hint, or refuses with a message saying when to come back. */
    public synchronized Allowance take(String username) {
        Instant now = clock.instant();
        Deque<Instant> times = byUser.computeIfAbsent(username, key -> new ArrayDeque<>());
        forgetOlderThan(times, now.minus(DAY));
        forgetOlderThan(server, now.minus(DAY));

        long lastHour = times.stream().filter(time -> time.isAfter(now.minus(HOUR))).count();
        if (times.size() >= perDay) {
            throw new LimitReachedException("You've used all " + perDay + " hints for today. Come back tomorrow, "
                    + "and meanwhile try the idea you have: a wrong answer teaches a lot too.");
        }
        if (lastHour >= perHour) {
            long minutes = Math.max(1, Duration.between(now, oldestWithin(times, now.minus(HOUR)).plus(HOUR)).toMinutes());
            throw new LimitReachedException("You've used " + perHour + " hints this hour. The next one is "
                    + "available in about " + minutes + " minute" + (minutes == 1 ? "" : "s") + ".");
        }
        if (server.size() >= serverPerDay) {
            throw new LimitReachedException("The tutor has answered as many questions as it can today. "
                    + "Try again tomorrow.");
        }
        times.addLast(now);
        server.addLast(now);
        return new Allowance((int) (perHour - lastHour - 1), perDay - times.size());
    }

    /** What is left without using anything. */
    public synchronized Allowance peek(String username) {
        Instant now = clock.instant();
        Deque<Instant> times = byUser.getOrDefault(username, new ArrayDeque<>());
        forgetOlderThan(times, now.minus(DAY));
        long lastHour = times.stream().filter(time -> time.isAfter(now.minus(HOUR))).count();
        return new Allowance((int) Math.max(0, perHour - lastHour), Math.max(0, perDay - times.size()));
    }

    /** Gives a hint back when the provider failed, so an outage does not use up the allowance. */
    public synchronized void giveBack(String username) {
        Deque<Instant> times = byUser.get(username);
        if (times != null && !times.isEmpty()) times.removeLast();
        if (!server.isEmpty()) server.removeLast();
    }

    private static void forgetOlderThan(Deque<Instant> times, Instant cutoff) {
        while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) times.removeFirst();
    }

    private static Instant oldestWithin(Deque<Instant> times, Instant cutoff) {
        for (Instant time : times) {
            if (time.isAfter(cutoff)) return time;
        }
        return cutoff;
    }
}
