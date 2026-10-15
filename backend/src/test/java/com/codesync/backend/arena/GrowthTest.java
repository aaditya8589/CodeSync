package com.codesync.backend.arena;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrowthTest {

    // A ladder like the problem files have: a few tiny examples, then 1 KB .. 1 MB doubling
    private static final List<Long> LADDER = ladder();

    private static List<Long> ladder() {
        List<Long> sizes = new ArrayList<>(List.of(10L, 20L, 40L));
        for (long size = 1024; size <= 1024 * 1024; size *= 2) sizes.add(size);
        return sizes;
    }

    /** Times from a cost model plus start-up, stopping at the time limit like the judge does. */
    private static Growth run(long startupMs, LongUnaryOperator workMs, long limitMs) {
        List<Growth.Point> finished = new ArrayList<>();
        for (long size : LADDER) {
            long millis = startupMs + workMs.applyAsLong(size);
            if (millis > limitMs) {
                return Growth.estimate(LADDER, finished, size);
            }
            finished.add(new Growth.Point(size, millis));
        }
        return Growth.estimate(LADDER, finished, null);
    }

    @Test
    void linearWorkReadsAsLinear() {
        // 1 MB of input in about 200 ms beyond start-up
        Growth growth = run(30, size -> size / 5000, 5000);
        assertEquals(Growth.Kind.MEASURED, growth.kind());
        assertEquals(1.0, growth.exponent().doubleValue(), 0.15);
        assertTrue(growth.summary().contains("linearly"), growth.summary());
        assertTrue(growth.detail().contains("×2."), growth.detail());
    }

    @Test
    void nLogNStillReadsAsAboutLinear() {
        Growth growth = run(5, size -> (long) (size * (Math.log(size) / Math.log(2)) / 60000), 5000);
        assertEquals(Growth.Kind.MEASURED, growth.kind());
        assertTrue(growth.exponent() < 1.3, String.valueOf(growth.exponent()));
    }

    @Test
    void quadraticWorkReadsAsQuadraticAndMentionsTheTimeout() {
        // About 1 s at 64 KB, so the 128 KB test runs out of a 2 s limit
        Growth growth = run(2, size -> size * size / 4_000_000L, 2000);
        assertEquals(Growth.Kind.MEASURED, growth.kind());
        assertEquals(2.0, growth.exponent().doubleValue(), 0.2);
        assertTrue(growth.summary().contains("quadratically"), growth.summary());
        assertTrue(growth.detail().contains("ran out of time"), growth.detail());
    }

    @Test
    void cubicWorkReadsAsCubicOrWorse() {
        // 27 ms at 8 KB, 1.8 s at 32 KB, then 64 KB runs out of time
        Growth growth = run(2, size -> size * size * size / 20_000_000_000L, 5000);
        assertEquals(Growth.Kind.MEASURED, growth.kind());
        assertTrue(growth.exponent() >= 2.5, String.valueOf(growth.exponent()));
        assertTrue(growth.summary().contains("O(n³)"));
    }

    @Test
    void aFastSolutionIsTooFastToMeasureRatherThanGuessed() {
        Growth growth = run(3, size -> size / 200_000, 2000);
        assertEquals(Growth.Kind.TOO_FAST, growth.kind());
        assertNull(growth.exponent());
        assertTrue(growth.detail().contains("1.0 MB"), growth.detail());
    }

    @Test
    void aSolutionThatTimesOutBeforeAnythingIsMeasurableSaysSo() {
        Growth growth = run(3, size -> size > 8192 ? 100_000 : 1, 2000);
        assertEquals(Growth.Kind.TIMED_OUT, growth.kind());
        assertTrue(growth.detail().contains("16 KB"), growth.detail());
    }

    @Test
    void aSlowColdStartOnATinyExampleIsNotMistakenForGrowth() {
        // Real timings from the judge: the first example paid ~1 s for a cold start
        List<Long> sizes = List.of(14L, 10L, 1056L, 21307L, 85114L, 340618L, 680885L, 1361344L);
        List<Growth.Point> finished = List.of(new Growth.Point(14, 971), new Growth.Point(10, 6),
                new Growth.Point(1056, 6), new Growth.Point(21307, 8), new Growth.Point(85114, 19),
                new Growth.Point(340618, 25), new Growth.Point(680885, 53), new Growth.Point(1361344, 115));
        Growth growth = Growth.estimate(sizes, finished, null);
        assertEquals(Growth.Kind.MEASURED, growth.kind());
        // Short runs are noisy (19 ms, 47 ms, 109 ms here), so "linear" has some room above 1
        assertTrue(growth.summary().contains("linearly"), growth.summary() + " " + growth.exponent());
    }

    @Test
    void sizesThatAreNotExactDoublingsStillCount() {
        // Python two-sum: 340 KB, 680 KB and 1.3 MB measurable, a span just under 4x
        List<Long> sizes = List.of(14L, 1056L, 170195L, 340618L, 680885L, 1361344L);
        List<Growth.Point> finished = List.of(new Growth.Point(14, 21), new Growth.Point(1056, 18),
                new Growth.Point(170195, 31), new Growth.Point(340618, 62), new Growth.Point(680885, 75),
                new Growth.Point(1361344, 121));
        assertEquals(Growth.Kind.MEASURED, Growth.estimate(sizes, finished, null).kind());
    }

    @Test
    void noLadderNoEstimate() {
        // Like "add two numbers": every test is a few bytes
        List<Long> sizes = List.of(8L, 10L, 12L, 40L);
        assertNull(Growth.estimate(sizes, List.of(new Growth.Point(8, 2), new Growth.Point(40, 2)), null));
    }

    @Test
    void sizesReadNicely() {
        assertEquals("512 bytes", Growth.bytes(512));
        assertEquals("64 KB", Growth.bytes(65536));
        assertEquals("2.5 MB", Growth.bytes(2_621_440));
    }
}
