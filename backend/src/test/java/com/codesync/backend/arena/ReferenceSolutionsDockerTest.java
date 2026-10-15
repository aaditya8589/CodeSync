package com.codesync.backend.arena;

import com.codesync.backend.execution.CodeRunner;
import com.codesync.backend.execution.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs an independent solution for every bundled problem through the real judge. If a problem's
 * expected outputs were wrong, these would not be accepted. Needs Docker and the images, like
 * CodeRunnerDockerTest.
 */
@EnabledIfEnvironmentVariable(named = "CODESYNC_DOCKER_TESTS", matches = "true")
class ReferenceSolutionsDockerTest {

    private final CodeRunner runner = new CodeRunner(List.of("docker"), Map.of(
            Language.CPP, image("CODESYNC_IMAGE_CPP", "gcc:14"),
            Language.PYTHON, image("CODESYNC_IMAGE_PYTHON", "python:3.13-slim"),
            Language.JAVA, image("CODESYNC_IMAGE_JAVA", "eclipse-temurin:21-jdk")
    ), Duration.ofSeconds(30), 64 * 1024);

    private static String image(String variable, String fallback) {
        String value = System.getenv(variable);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String solution(String file) {
        try (InputStream stream = ReferenceSolutionsDockerTest.class.getResourceAsStream("/solutions/" + file)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private Judge.Result judge(String slug, Language language, String source) {
        ProblemFile problem = ProblemCatalog.loadAll().stream()
                .filter(file -> file.slug().equals(slug)).findFirst().orElseThrow();
        CodeRunner.TestRun run = runner.runTests(language, source, problem.tests().stream().map(ProblemFile.Test::input).toList());
        return Judge.judge(problem.tests(), run, problem.expectedComplexity());
    }

    @Test
    void everyProblemAcceptsItsReferenceSolution() {
        for (ProblemFile problem : ProblemCatalog.loadAll()) {
            Judge.Result result = judge(problem.slug(), Language.CPP, solution(problem.slug() + ".cpp"));
            assertEquals(Verdict.ACCEPTED, result.verdict(),
                    problem.slug() + ": failed test " + result.failedTest() + " " + result.compileOutput());
            assertEquals(problem.tests().size(), result.passed(), problem.slug());
        }
    }

    @Test
    void pythonAndJavaSolutionsAreAcceptedToo() {
        assertEquals(Verdict.ACCEPTED, judge("two-sum", Language.PYTHON, solution("two-sum.py")).verdict());
        assertEquals(Verdict.ACCEPTED, judge("maximum-subarray", Language.JAVA, solution("MaximumSubarray.java")).verdict());
    }

    @Test
    void aWrongSolutionIsCaught() {
        String wrong = solution("add-two-numbers.cpp").replace("a + b", "a - b");
        Judge.Result result = judge("add-two-numbers", Language.CPP, wrong);
        assertEquals(Verdict.WRONG_ANSWER, result.verdict());
    }

    @Test
    void overflowingIntIsCaughtByTheHiddenTests() {
        // Passes the small examples but not the hidden tests with large sums
        String overflow = solution("maximum-subarray.cpp").replace("long long", "int");
        Judge.Result result = judge("maximum-subarray", Language.CPP, overflow);
        assertEquals(Verdict.WRONG_ANSWER, result.verdict());
        assertEquals(true, result.tests().get(0).sample() && result.tests().get(0).verdict() == Verdict.ACCEPTED);
    }

    @Test
    void tryingEveryPairRunsOutOfTimeAndReadsAsQuadratic() {
        // Correct, but O(n^2): fine on the small tests, too slow at the top of the size ladder
        Judge.Result result = judge("two-sum", Language.CPP, solution("two-sum-brute-force.cpp"));
        assertEquals(Verdict.TIME_LIMIT_EXCEEDED, result.verdict());
        assertEquals("O(n)", result.expectedComplexity());
        assertEquals(Growth.Kind.MEASURED, result.growth().kind());
        assertTrue(result.growth().summary().contains("quadratically"), result.growth().toString());
        assertTrue(result.growth().detail().contains("ran out of time"), result.growth().detail());
    }

    @Test
    void theReferenceSolutionReadsAsLinear() {
        Judge.Result result = judge("two-sum", Language.CPP, solution("two-sum.cpp"));
        assertEquals(Verdict.ACCEPTED, result.verdict());
        // On a fast machine the whole ladder may finish too quickly to measure, which is also fine
        assertTrue(result.growth().kind() == Growth.Kind.TOO_FAST || result.growth().summary().contains("linearly"),
                result.growth().toString());
    }
}
