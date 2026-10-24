package com.codesync.backend.arena;

import java.util.Comparator;
import java.util.List;

/**
 * How a solution's running time grows with the size of its input, measured from the judge's own
 * per-test timings. The hidden tests come in a ladder of doubling sizes; fitting time against
 * size on a log-log scale gives the exponent k in time ~ size^k: about 1 is linear, about 2
 * quadratic. Nothing is run twice and no code is read, so this only says what the clock saw.
 *
 * Small runs are mostly process start-up and noise, so only the larger tests that took clearly
 * longer than the fastest one count. (The very first test also pays for a cold start, which once
 * made a 14-byte example look slower than a 1 MB test.) When too few count, the honest answer is
 * "too fast to measure", not a guess.
 */
public record Growth(Kind kind, Double exponent, String summary, String detail) {

    public enum Kind { MEASURED, TOO_FAST, TIMED_OUT }

    /** One finished test: input size in bytes, wall time in milliseconds. */
    public record Point(long size, long millis) {
    }

    // Below this much time beyond start-up, a measurement is mostly noise
    static final long MIN_EXCESS_MS = 15;
    // The tests used for a fit must span about two doublings (sizes are not exact powers of two)
    static final double MIN_FIT_SPAN = 3.5;
    // Tests smaller than the problem's largest divided by this are examples, not part of the ladder
    static final long LADDER_FRACTION = 256;
    // How many of the largest measurable tests the fit uses
    static final int FIT_POINTS = 3;
    // A problem whose tests vary less than this in size has nothing to measure (e.g. "add two numbers")
    static final double MIN_LADDER_SPAN = 8.0;

    /**
     * @param sizes        the input size of every test of the problem, run or not
     * @param finished     the tests that ran to completion (any output), in any order
     * @param timedOutSize the input size of the test that ran out of time, if one did
     * @return null when the problem's tests are not a ladder of sizes
     */
    public static Growth estimate(List<Long> sizes, List<Point> finished, Long timedOutSize) {
        long smallest = sizes.stream().mapToLong(Long::longValue).filter(size -> size > 0).min().orElse(0);
        long largest = sizes.stream().mapToLong(Long::longValue).max().orElse(0);
        if (smallest == 0 || (double) largest / smallest < MIN_LADDER_SPAN) {
            return null;
        }

        long baseline = finished.stream().mapToLong(Point::millis).min().orElse(0);
        List<Point> measurable = finished.stream()
                .filter(point -> point.size() > 0 && point.size() * LADDER_FRACTION >= largest)
                .filter(point -> point.millis() - baseline >= MIN_EXCESS_MS)
                // The largest tests only: on smaller ones the time is mostly reading the input,
                // which is linear whatever the algorithm, and that pulls a quadratic fit down
                .sorted(Comparator.comparingLong(Point::size).reversed())
                .limit(FIT_POINTS)
                .toList();
        long fitSmallest = measurable.stream().mapToLong(Point::size).min().orElse(0);
        long fitLargest = measurable.stream().mapToLong(Point::size).max().orElse(0);

        if (measurable.size() >= 3 && (double) fitLargest / fitSmallest >= MIN_FIT_SPAN) {
            double k = Math.max(0, Math.min(4, slope(measurable, baseline)));
            String detail = "Doubling the input multiplied the running time by about ×"
                    + oneDecimal(Math.pow(2, k)) + " (measured on the " + measurable.size() + " largest tests that finished)."
                    + (timedOutSize == null ? "" : " Then a test of " + bytes(timedOutSize) + " ran out of time.");
            return new Growth(Kind.MEASURED, Math.round(k * 10) / 10.0, label(k), detail);
        }
        if (timedOutSize != null) {
            return new Growth(Kind.TIMED_OUT, null, "Grows too fast for the larger tests",
                    "The smaller tests were too quick to measure, then a test of " + bytes(timedOutSize)
                            + " ran out of time.");
        }
        long biggestRun = finished.stream().mapToLong(Point::size).max().orElse(0);
        return new Growth(Kind.TOO_FAST, null, "Too fast to measure here",
                "Even the largest test (" + bytes(biggestRun) + ") finished within " + MIN_EXCESS_MS
                        + " ms of the quickest one, so there is no growth to see at these sizes.");
    }

    static String label(double k) {
        if (k < 1.4) return "Grows about linearly (like O(n) or O(n log n))";
        if (k < 1.7) return "Grows faster than linear (between O(n) and O(n²))";
        if (k < 2.5) return "Grows about quadratically (like O(n²))";
        return "Grows like O(n³) or worse";
    }

    // Least squares on log(time beyond start-up) against log(size)
    private static double slope(List<Point> points, long baseline) {
        int n = points.size();
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;
        for (Point point : points) {
            double x = Math.log(point.size());
            double y = Math.log(point.millis() - baseline);
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }
        return (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);
    }

    private static String oneDecimal(double value) {
        return String.valueOf(Math.round(value * 10) / 10.0);
    }

    static String bytes(long size) {
        if (size >= 1024 * 1024) return oneDecimal(size / (1024.0 * 1024)) + " MB";
        if (size >= 1024) return Math.round(size / 1024.0) + " KB";
        return size + " bytes";
    }
}
