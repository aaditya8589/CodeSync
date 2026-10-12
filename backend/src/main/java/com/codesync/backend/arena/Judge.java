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
            List<TestResult> tests
    ) {
    }

    private Judge() {
    }

    public static Result judge(List<ProblemFile.Test> tests, CodeRunner.TestRun run) {
        int total = tests.size();

        if (run.failure() == ExecutionStatus.COMPILE_ERROR) {
            return new Result(Verdict.COMPILE_ERROR, 0, total, null, 0, run.message(), List.of());
        }
        if (run.failure() == ExecutionStatus.INTERNAL_ERROR) {
            return new Result(Verdict.INTERNAL_ERROR, 0, total, null, 0, run.message(), List.of());
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

        return new Result(overall, passed, total, failedTest, maxTime, "", List.copyOf(results));
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
