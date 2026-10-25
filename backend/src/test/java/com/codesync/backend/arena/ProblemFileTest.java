package com.codesync.backend.arena;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProblemFileTest {

    private static final String EXAMPLE = """
            title: Add Two Numbers
            difficulty: Easy
            position: 1
            === statement
            Read two integers.

            Print their sum.
            === input
            One line with `a` and `b`.
            === output
            Print `a + b`.
            === constraints
            -10^18 <= a, b <= 10^18
            === sample
            3 4
            --- expected
            7
            === test
            1 2
            3 4
            --- expected
            3
            7
            """;

    @Test
    void parsesHeaderSectionsAndTests() {
        ProblemFile problem = ProblemFile.parse("add-two-numbers", EXAMPLE);

        assertEquals("Add Two Numbers", problem.title());
        assertEquals("Easy", problem.difficulty());
        assertEquals(1, problem.position());
        assertEquals("Read two integers.\n\nPrint their sum.", problem.statement());
        assertEquals("One line with `a` and `b`.", problem.inputFormat());
        assertEquals("-10^18 <= a, b <= 10^18", problem.constraints());
        assertEquals(List.of(
                new ProblemFile.Test(true, "3 4\n", "7\n"),
                new ProblemFile.Test(false, "1 2\n3 4\n", "3\n7\n")
        ), problem.tests());
    }

    @Test
    void rejectsFilesWithoutExamplesOrExpectedOutput() {
        assertThrows(IllegalArgumentException.class, () -> ProblemFile.parse("x",
                EXAMPLE.substring(0, EXAMPLE.indexOf("=== sample"))));
        assertThrows(IllegalArgumentException.class, () -> ProblemFile.parse("x",
                EXAMPLE.replace("--- expected\n7\n", "")));
        assertThrows(IllegalArgumentException.class, () -> ProblemFile.parse("x",
                EXAMPLE.replace("title: Add Two Numbers\n", "")));
    }

    @Test
    void everyBundledProblemIsWellFormed() {
        List<ProblemFile> problems = ProblemCatalog.loadAll();
        assertEquals(21, problems.size());

        Set<Integer> positions = new HashSet<>();
        for (ProblemFile problem : problems) {
            assertTrue(positions.add(problem.position()), "duplicate position in " + problem.slug());
            assertTrue(problem.tests().size() >= 8, problem.slug() + " has too few tests");
            assertTrue(problem.tests().stream().anyMatch(ProblemFile.Test::sample), problem.slug());
            assertTrue(problem.tests().stream().anyMatch(test -> !test.sample()), problem.slug());
            assertTrue(problem.expectedComplexity() != null && problem.expectedComplexity().startsWith("O("),
                    problem.slug() + " names no expected complexity");
            assertEquals(64, problem.contentHash().length(), problem.slug());
            for (ProblemFile.Test test : problem.tests()) {
                assertTrue(test.input().endsWith("\n"), problem.slug());
                assertFalse(test.expectedOutput().isBlank(), problem.slug());
                assertTrue(test.expectedOutput().getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                        < com.codesync.backend.execution.CodeRunner.MAX_TEST_OUTPUT_BYTES, problem.slug());
            }
        }
    }

    @Test
    void theComplexityHeaderIsOptionalAndTheHashFollowsTheText() {
        ProblemFile plain = ProblemFile.parse("x", EXAMPLE);
        assertEquals(null, plain.expectedComplexity());

        ProblemFile named = ProblemFile.parse("x", EXAMPLE.replace("title: Add Two Numbers\n",
                "title: Add Two Numbers\ncomplexity: O(1)\n"));
        assertEquals("O(1)", named.expectedComplexity());
        assertFalse(plain.contentHash().equals(named.contentHash()));
        assertEquals(plain.contentHash(), ProblemFile.parse("x", EXAMPLE).contentHash());
    }
}
