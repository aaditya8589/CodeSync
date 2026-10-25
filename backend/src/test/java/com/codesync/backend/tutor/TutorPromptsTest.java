package com.codesync.backend.tutor;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TutorPromptsTest {

    private static final TutorPrompts.Context CONTEXT = new TutorPrompts.Context(
            "Two Sum", "Find two positions.", "n and target, then n numbers.", "Print i j.",
            "2 <= n <= 131072", "O(n)", List.of(new TutorPrompts.Example("4 9\n2 7 11 15\n", "0 1\n")),
            "main.cpp", "C++", "int main() { /* brute force */ }",
            new TutorPrompts.LastAttempt("TIME_LIMIT_EXCEEDED", 16, 17, 17, false,
                    "Grows about quadratically (like O(n²))", "Doubling the input multiplied the running time by about ×4.0."));

    @Test
    void theSystemPromptCarriesRulesProblemCodeAndVerdict() {
        String system = TutorPrompts.system(CONTEXT);
        assertTrue(system.contains("Never write code"));
        assertTrue(system.contains("## Problem: Two Sum"));
        assertTrue(system.contains("A good solution runs in O(n)"));
        assertTrue(system.contains("Example 1 input:\n4 9\n2 7 11 15"));
        assertTrue(system.contains("(C++, file main.cpp)\n<<<\nint main() { /* brute force */ }\n>>>"));
        assertTrue(system.contains("time limit exceeded, 16 of 17 tests passed, first failing test: 17 (hidden)."));
        assertTrue(system.contains("Grows about quadratically"));
    }

    @Test
    void emptyCodeAndNoSubmissionAreSaidPlainly() {
        TutorPrompts.Context fresh = new TutorPrompts.Context("Two Sum", "S", "I", "O", "", null, List.of(),
                "main.py", "Python", "  ", null);
        String system = TutorPrompts.system(fresh);
        assertTrue(system.contains("(They have not written anything yet.)"));
        assertTrue(system.contains("None yet."));
        assertFalse(system.contains("A good solution runs in"));
    }

    @Test
    void longCodeIsCut() {
        String code = "x".repeat(TutorPrompts.MAX_CODE_CHARS + 50);
        TutorPrompts.Context big = new TutorPrompts.Context("T", "S", "I", "O", "", null, List.of(),
                "a.py", "Python", code, null);
        String system = TutorPrompts.system(big);
        assertTrue(system.contains("only the beginning is shown"));
        assertFalse(system.contains("x".repeat(TutorPrompts.MAX_CODE_CHARS + 1)));
    }

    @Test
    void turnsStartWithTheStudentAlternateAndEndWithTheRequest() {
        List<TutorMessage> history = List.of(
                new TutorMessage(TutorMessage.Role.TUTOR, "orphan answer"),
                new TutorMessage(TutorMessage.Role.STUDENT, "first"),
                new TutorMessage(TutorMessage.Role.STUDENT, "second"),
                new TutorMessage(TutorMessage.Role.TUTOR, "answer"),
                new TutorMessage(TutorMessage.Role.STUDENT, "dangling"));
        List<TutorMessage> turns = TutorPrompts.turns(history, TutorPrompts.Level.ASK, "Why is mine slow?");

        assertEquals(3, turns.size());
        assertEquals(new TutorMessage(TutorMessage.Role.STUDENT, "first\n\nsecond"), turns.get(0));
        assertEquals(TutorMessage.Role.TUTOR, turns.get(1).role());
        assertEquals(TutorMessage.Role.STUDENT, turns.get(2).role());
        assertTrue(turns.get(2).text().endsWith("My question: Why is mine slow?"));
    }

    @Test
    void onlyRecentHistoryIsSentAndQuestionsAreClipped() {
        List<TutorMessage> history = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) {
            history.add(new TutorMessage(i % 2 == 0 ? TutorMessage.Role.STUDENT : TutorMessage.Role.TUTOR, "turn " + i));
        }
        List<TutorMessage> turns = TutorPrompts.turns(history, TutorPrompts.Level.NUDGE,
                "q".repeat(TutorPrompts.MAX_QUESTION_CHARS + 100));
        assertTrue(turns.size() <= TutorPrompts.MAX_HISTORY_TURNS + 1);
        assertEquals("turn 14", turns.getFirst().text());
        assertTrue(turns.getLast().text().startsWith(TutorPrompts.Level.NUDGE.request));
        assertTrue(turns.getLast().text().length() < TutorPrompts.Level.NUDGE.request.length() + TutorPrompts.MAX_QUESTION_CHARS + 40);
    }
}
