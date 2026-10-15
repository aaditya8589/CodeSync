package com.codesync.backend.arena;

import com.codesync.backend.execution.CodeRunner;
import com.codesync.backend.execution.ExecutionStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JudgeTest {

    private static final List<ProblemFile.Test> TESTS = List.of(
            new ProblemFile.Test(true, "1 2\n", "3\n"),
            new ProblemFile.Test(false, "secret input\n", "10\n"),
            new ProblemFile.Test(false, "4 4\n", "8\n")
    );

    private static CodeRunner.TestOutcome ok(String stdout) {
        return new CodeRunner.TestOutcome(ExecutionStatus.SUCCESS, 0, 5, stdout, "", false);
    }

    private static CodeRunner.TestRun ran(CodeRunner.TestOutcome... outcomes) {
        return new CodeRunner.TestRun(null, "", List.of(outcomes), 100);
    }

    @Test
    void outputComparisonIgnoresTrailingSpacesAndBlankLinesOnly() {
        assertTrue(Judge.outputsMatch("3\n", "3"));
        assertTrue(Judge.outputsMatch("3\n", "3   \n\n\n"));
        assertTrue(Judge.outputsMatch("1 2\n3\n", "1 2\r\n3\r\n"));
        assertFalse(Judge.outputsMatch("3\n", " 3\n"));
        assertFalse(Judge.outputsMatch("1 2\n", "1  2\n"));
        assertFalse(Judge.outputsMatch("1\n2\n", "1\n\n2\n"));
        assertFalse(Judge.outputsMatch("3\n", ""));
    }

    @Test
    void allCorrectIsAccepted() {
        Judge.Result result = Judge.judge(TESTS, ran(ok("3\n"), ok("10\n"), ok("8\n")));
        assertEquals(Verdict.ACCEPTED, result.verdict());
        assertEquals(3, result.passed());
        assertNull(result.failedTest());
    }

    @Test
    void verdictIsTheFirstFailingTest() {
        Judge.Result result = Judge.judge(TESTS, ran(ok("3\n"), ok("11\n"),
                new CodeRunner.TestOutcome(ExecutionStatus.RUNTIME_ERROR, 1, 3, "", "boom", false)));
        assertEquals(Verdict.WRONG_ANSWER, result.verdict());
        assertEquals(Integer.valueOf(2), result.failedTest());
        assertEquals(1, result.passed());
        assertEquals(Verdict.RUNTIME_ERROR, result.tests().get(2).verdict());
    }

    @Test
    void hiddenTestsRevealNothingButTheVerdict() {
        Judge.Result result = Judge.judge(TESTS, ran(ok("3\n"),
                new CodeRunner.TestOutcome(ExecutionStatus.RUNTIME_ERROR, 1, 3, "", "secret input", false), ok("8\n")));
        Judge.TestResult sample = result.tests().get(0);
        Judge.TestResult hidden = result.tests().get(1);

        assertEquals("1 2\n", sample.input());
        assertEquals("3\n", sample.actualOutput());
        assertNull(hidden.input());
        assertNull(hidden.expectedOutput());
        assertNull(hidden.actualOutput());
        assertNull(hidden.errorOutput());
    }

    @Test
    void stoppingAtATimeLimitLeavesLaterTestsUnjudged() {
        Judge.Result result = Judge.judge(TESTS, ran(ok("3\n"),
                new CodeRunner.TestOutcome(ExecutionStatus.TIME_LIMIT_EXCEEDED, 124, 2000, "", "", false)));
        assertEquals(Verdict.TIME_LIMIT_EXCEEDED, result.verdict());
        assertEquals(Integer.valueOf(2), result.failedTest());
        assertEquals(2, result.tests().size());
    }

    @Test
    void wholeRunStoppedCountsAsTimeLimitOnTheNextTest() {
        Judge.Result result = Judge.judge(TESTS,
                new CodeRunner.TestRun(ExecutionStatus.TIME_LIMIT_EXCEEDED, "Stopped", List.of(ok("3\n")), 30_000));
        assertEquals(Verdict.TIME_LIMIT_EXCEEDED, result.verdict());
        assertEquals(Integer.valueOf(2), result.failedTest());
    }

    @Test
    void compileErrorStopsEverything() {
        Judge.Result result = Judge.judge(TESTS,
                new CodeRunner.TestRun(ExecutionStatus.COMPILE_ERROR, "main.cpp:1: error", List.of(), 900));
        assertEquals(Verdict.COMPILE_ERROR, result.verdict());
        assertEquals("main.cpp:1: error", result.compileOutput());
        assertEquals(0, result.passed());
        assertTrue(result.tests().isEmpty());
    }

    @Test
    void memoryAndTimeOnEachTestAreReported() {
        Judge.Result result = Judge.judge(TESTS, ran(ok("3\n"), ok("10\n"),
                new CodeRunner.TestOutcome(ExecutionStatus.MEMORY_LIMIT_EXCEEDED, 137, 700, "", "", false)));
        assertEquals(Verdict.MEMORY_LIMIT_EXCEEDED, result.verdict());
        assertEquals(700, result.maxTimeMs());
    }

    @Test
    void aQuadraticSolutionThatTimesOutGetsAGrowthEstimate() {
        // A ladder of hidden tests, 1 KB .. 64 KB; time grows 4x per doubling until 64 KB runs out of time
        List<ProblemFile.Test> ladder = new ArrayList<>();
        List<CodeRunner.TestOutcome> outcomes = new ArrayList<>();
        long millis = 20;
        for (int size = 1024; size <= 64 * 1024; size *= 2) {
            ladder.add(new ProblemFile.Test(false, "x".repeat(size), "ok\n"));
            if (size < 64 * 1024) {
                outcomes.add(new CodeRunner.TestOutcome(ExecutionStatus.SUCCESS, 0, 5 + millis, "ok\n", "", false));
                millis *= 4;
            } else {
                outcomes.add(new CodeRunner.TestOutcome(ExecutionStatus.TIME_LIMIT_EXCEEDED, 124, 2000, "", "", false));
            }
        }

        Judge.Result result = Judge.judge(ladder, new CodeRunner.TestRun(null, "", outcomes, 100), "O(n)");

        assertEquals(Verdict.TIME_LIMIT_EXCEEDED, result.verdict());
        assertEquals("O(n)", result.expectedComplexity());
        assertEquals(Growth.Kind.MEASURED, result.growth().kind());
        assertTrue(result.growth().summary().contains("quadratically"), result.growth().summary());
        assertTrue(result.growth().detail().contains("64 KB ran out of time"), result.growth().detail());
    }

    @Test
    void aCompileErrorStillNamesTheExpectedComplexityButMeasuresNothing() {
        Judge.Result result = Judge.judge(TESTS,
                new CodeRunner.TestRun(ExecutionStatus.COMPILE_ERROR, "error: ';' expected", List.of(), 100), "O(n log n)");
        assertEquals("O(n log n)", result.expectedComplexity());
        assertNull(result.growth());
    }

    @Test
    void smallTestsOfOneSizeGiveNoEstimate() {
        assertNull(Judge.judge(TESTS, ran(ok("3\n"), ok("10\n"), ok("8\n"))).growth());
    }
}
