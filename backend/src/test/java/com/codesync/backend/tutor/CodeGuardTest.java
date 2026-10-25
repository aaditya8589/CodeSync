package com.codesync.backend.tutor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeGuardTest {

    private static String guard(String... chunks) {
        CodeGuard guard = new CodeGuard();
        StringBuilder out = new StringBuilder();
        for (String chunk : chunks) out.append(guard.accept(chunk));
        return out.append(guard.finish()).toString();
    }

    @Test
    void proseAndShortNamesPassUntouched() {
        String text = """
                Think about what you know at each day: the cheapest price so far.
                If today's price minus that minimum beats your best, keep it.
                - Use a variable like `best` and compare against `dp[i]`.
                1. Sort the stretches by their start, which costs `O(n log n)`.
                For each start, ask: where does the window end?
                Return the answer once the loop is done, and check `i < j`.
                """;
        assertEquals(text, guard(text));
    }

    @Test
    void codeBlocksBecomeOneNote() {
        String out = guard("Here is how:\n```cpp\nint best = 0;\nfor (int i = 0; i < n; i++) {\n}\n```\nNow you try.\n");
        assertEquals("Here is how:\n" + CodeGuard.NOTE + "\nNow you try.\n", out);
    }

    @Test
    void codeLinesWithoutFencesAreRemovedInEveryLanguage() {
        String[] code = {
                "#include <vector>", "import sys", "from collections import deque",
                "def solve(nums):", "for i in range(n):", "while lo < hi:", "if seen[x]:",
                "    return best", "best = max(best, price - cheapest)", "dp[i] += dp[i - 1] + 1",
                "int main() {", "}", "std::sort(a.begin(), a.end());", "print(ans)",
                "System.out.println(ans);", "public static void main(String[] args) {",
                "for (int j = i + 1; j < n; j++)", "cout << best << endl;", "  - total += x;",
        };
        for (String line : code) {
            CodeGuard guard = new CodeGuard();
            String out = guard.accept("Look:\n" + line + "\nDone.\n") + guard.finish();
            assertEquals("Look:\n" + CodeGuard.NOTE + "\nDone.\n", out, line);
            assertTrue(guard.removedAny(), line);
        }
    }

    @Test
    void longInlineCodeIsShortened() {
        String out = guard("Write `for (int i = 0; i < n; i++) total += a[i];` and `best = max(best, x)` here.\n");
        assertEquals("Write _(code removed)_ and _(code removed)_ here.\n", out);
    }

    @Test
    void worksWhenLinesArriveInPieces() {
        String out = guard("Try a hash ", "map.\nseen", "[x] = i;\nThen ", "check it.");
        assertEquals("Try a hash map.\n" + CodeGuard.NOTE + "\nThen check it.", out);
    }

    @Test
    void anUnclosedBlockHidesEverythingAfterIt() {
        String out = guard("Start\n```\nx = 1\n", "y = 2\nmore text");
        assertEquals("Start\n" + CodeGuard.NOTE + "\n", out);
    }

    @Test
    void theNoteShowsOncePerRemovedStretch() {
        String out = guard("a\nx = y + 1;\ny = z * 2;\nb\nz = q - 1;\nc\n");
        assertEquals("a\n" + CodeGuard.NOTE + "\nb\n" + CodeGuard.NOTE + "\nc\n", out);
    }

    @Test
    void plainProseReportsNothingRemoved() {
        CodeGuard guard = new CodeGuard();
        guard.accept("Keep going, you're close.\n");
        assertFalse(guard.removedAny());
    }
}
