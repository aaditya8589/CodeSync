package com.codesync.backend.arena;

import com.codesync.backend.execution.CodeRunner;
import com.codesync.backend.execution.ExecutionStatus;

import java.util.ArrayList;
import java.util.List;

/** Turns a program's outputs into verdicts. No I/O, so every rule here is unit-tested. */
public final class Judge {

    // Shown for examples only; hidden tests never reveal their input or output
    static final int MAX_SHOWN_CHARS = 2_000;

    public record TestResult(
            int number,
            boolean sample,
            Verdict verdict,
            long timeMs,
            String input,
            String expectedOutput,
            String actualOutput,
            String errorOutput
    ) {
    }

    public record Result(
            Verdict verdict,
            int passed,
            int total,
            Integer failedTest,
            long maxTimeMs,
            String compileOutput,
            List<TestResult> tests,
            // What a good solution achieves, e.g. "O(n)"; null if the problem names none
            String expectedComplexity,
            // How this run's time grew with input size; null when there was nothing to measure
            Growth growth
    ) {
    }

    private Judge() {
    }

    public static Result judge(List<ProblemFile.Test> tests, CodeRunner.TestRun run) {
        return judge(tests, run, null);
    }

    public static Result judge(List<ProblemFile.Test> tests, CodeRunner.TestRun run, String expectedComplexity) {
        int total = tests.size();

        if (run.failure() == ExecutionStatus.COMPILE_ERROR) {
            return new Result(Verdict.COMPILE_ERROR, 0, total, null, 0, run.message(), List.of(), expectedComplexity, null);
        }
        if (run.failure() == ExecutionStatus.INTERNAL_ERROR) {
            return new Result(Verdict.INTERNAL_ERROR, 0, total, null, 0, run.message(), List.of(), expectedComplexity, null);
        }

        List<TestResult> results = new ArrayList<>();
        Verdict overall = Verdict.ACCEPTED;
        Integer failedTest = null;
        int passed = 0;
        long maxTime = 0;

        for (int i = 0; i < total; i++) {
            ProblemFile.Test test = tests.get(i);
            Verdict verdict;
            long timeMs = 0;
            String actual = "";
            String errors = "";

            if (i < run.outcomes().size()) {
                CodeRunner.TestOutcome outcome = run.outcomes().get(i);
                timeMs = outcome.timeMs();
                actual = outcome.stdout();
                errors = outcome.stderr();
                verdict = verdictFor(outcome, test.expectedOutput());
            } else {
                // Not run: an earlier test hit a limit, or the whole run was stopped
                verdict = run.failure() == ExecutionStatus.TIME_LIMIT_EXCEEDED && i == run.outcomes().size()
                        ? Verdict.TIME_LIMIT_EXCEEDED
                        : null;
            }

            if (verdict == Verdict.ACCEPTED) passed++;
            if (verdict != null && verdict != Verdict.ACCEPTED && failedTest == null) {
                overall = verdict;
                failedTest = i + 1;
            }
            maxTime = Math.max(maxTime, timeMs);

            if (verdict != null) {
                results.add(new TestResult(
                        i + 1,
                        test.sample(),
                        verdict,
                        timeMs,
                        test.sample() ? shorten(test.input()) : null,
                        test.sample() ? shorten(test.expectedOutput()) : null,
                        test.sample() ? shorten(actual) : null,
                        // Not even stderr for hidden tests: a program could print its input there
                        test.sample() ? shorten(errors) : null
                ));
            }
        }

        if (failedTest == null && passed < total) {
            // Stopped before reaching some tests without a verdict for them
            overall = Verdict.TIME_LIMIT_EXCEEDED;
            failedTest = passed + 1;
        }

        return new Result(overall, passed, total, failedTest, maxTime, "", List.copyOf(results),
                expectedComplexity, growth(tests, run));
    }

    // Timings of the tests that ran to the end; a crash or a limit says nothing about growth
    private static Growth growth(List<ProblemFile.Test> tests, CodeRunner.TestRun run) {
        List<Long> sizes = tests.stream().map(test -> (long) test.input().length()).toList();
        List<Growth.Point> finished = new ArrayList<>();
        Long timedOut = null;
        for (int i = 0; i < run.outcomes().size() && i < tests.size(); i++) {
            CodeRunner.TestOutcome outcome = run.outcomes().get(i);
            if (outcome.status() == ExecutionStatus.SUCCESS) {
                finished.add(new Growth.Point(sizes.get(i), outcome.timeMs()));
            } else if (outcome.status() == ExecutionStatus.TIME_LIMIT_EXCEEDED && timedOut == null) {
                timedOut = sizes.get(i);
            }
        }
        if (timedOut == null && run.failure() == ExecutionStatus.TIME_LIMIT_EXCEEDED
                && run.outcomes().size() < tests.size()) {
            timedOut = sizes.get(run.outcomes().size());
        }
        return Growth.estimate(sizes, finished, timedOut);
    }

    static Verdict verdictFor(CodeRunner.TestOutcome outcome, String expected) {
        return switch (outcome.status()) {
            case SUCCESS -> outputsMatch(expected, outcome.stdout()) ? Verdict.ACCEPTED : Verdict.WRONG_ANSWER;
            case TIME_LIMIT_EXCEEDED -> Verdict.TIME_LIMIT_EXCEEDED;
            case MEMORY_LIMIT_EXCEEDED -> Verdict.MEMORY_LIMIT_EXCEEDED;
            case COMPILE_ERROR, INTERNAL_ERROR -> Verdict.INTERNAL_ERROR;
            case RUNTIME_ERROR -> Verdict.RUNTIME_ERROR;
        };
    }

    /**
     * Line by line, ignoring spaces at the end of a line, Windows line endings and blank lines at
     * the end, which is what people expect from "print the answer". Everything else must match.
     */
    static boolean outputsMatch(String expected, String actual) {
        return normalize(expected).equals(normalize(actual));
    }

    private static List<String> normalize(String text) {
        List<String> lines = new ArrayList<>();
        for (String line : text.replace("\r\n", "\n").split("\n", -1)) {
            lines.add(line.stripTrailing());
        }
        while (!lines.isEmpty() && lines.getLast().isEmpty()) {
            lines.removeLast();
        }
        return lines;
    }

    private static String shorten(String text) {
        return text.length() <= MAX_SHOWN_CHARS ? text : text.substring(0, MAX_SHOWN_CHARS) + "\n...";
    }
}
