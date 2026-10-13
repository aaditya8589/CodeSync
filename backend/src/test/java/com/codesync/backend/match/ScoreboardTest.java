package com.codesync.backend.match;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreboardTest {

    private static final Instant START = Instant.parse("2026-10-14T10:00:00Z");
    private static final List<String> PROBLEMS = List.of("two-sum", "valid-brackets");

    private static Scoreboard.Attempt at(String user, String problem, String verdict, int minute, int second) {
        return new Scoreboard.Attempt(user, problem, verdict, START.plusSeconds(minute * 60L + second));
    }

    private static List<Scoreboard.Row> board(List<String> players, Scoreboard.Attempt... attempts) {
        return Scoreboard.compute(PROBLEMS, players, List.of(attempts), START);
    }

    @Test
    void moreSolvedRanksHigherWhateverThePenalty() {
        List<Scoreboard.Row> rows = board(List.of("ada", "bob"),
                at("ada", "two-sum", "ACCEPTED", 50, 0),
                at("ada", "valid-brackets", "ACCEPTED", 59, 0),
                at("bob", "two-sum", "ACCEPTED", 1, 0));

        assertEquals("ada", rows.get(0).username());
        assertEquals(2, rows.get(0).solved());
        assertEquals(109, rows.get(0).penalty());
        assertEquals(2, rows.get(1).rank());
    }

    @Test
    void rejectedAttemptsBeforeAcceptedAddFiveMinutesEach() {
        Scoreboard.Row ada = board(List.of("ada"),
                at("ada", "two-sum", "WRONG_ANSWER", 3, 0),
                at("ada", "two-sum", "TIME_LIMIT_EXCEEDED", 6, 0),
                at("ada", "two-sum", "ACCEPTED", 10, 30)).get(0);

        assertEquals(1, ada.solved());
        assertEquals(10 + 2 * Scoreboard.PENALTY_MINUTES, ada.penalty());
        Scoreboard.Cell cell = ada.cells().get(0);
        assertTrue(cell.solved());
        assertEquals(2, cell.rejected());
        assertEquals(10, cell.solvedAtMinute().longValue());
    }

    @Test
    void compileErrorsAndJudgeFailuresCostNothing() {
        Scoreboard.Row ada = board(List.of("ada"),
                at("ada", "two-sum", "COMPILE_ERROR", 1, 0),
                at("ada", "two-sum", "INTERNAL_ERROR", 2, 0),
                at("ada", "two-sum", "ACCEPTED", 4, 0)).get(0);
        assertEquals(4, ada.penalty());
        assertEquals(0, ada.cells().get(0).rejected());
    }

    @Test
    void submissionsAfterAcceptedAndOnUnsolvedProblemsAddNoPenalty() {
        Scoreboard.Row ada = board(List.of("ada"),
                at("ada", "two-sum", "ACCEPTED", 4, 0),
                at("ada", "two-sum", "WRONG_ANSWER", 5, 0),
                at("ada", "valid-brackets", "WRONG_ANSWER", 6, 0),
                at("ada", "valid-brackets", "WRONG_ANSWER", 7, 0)).get(0);

        assertEquals(4, ada.penalty());
        Scoreboard.Cell unsolved = ada.cells().get(1);
        assertFalse(unsolved.solved());
        assertEquals(2, unsolved.rejected());
        assertNull(unsolved.solvedAtMinute());
    }

    @Test
    void equalSolvedAndPenaltyGoesToWhoeverGotThereFirst() {
        List<Scoreboard.Row> rows = board(List.of("ada", "bob"),
                at("bob", "two-sum", "ACCEPTED", 10, 50),
                at("ada", "two-sum", "ACCEPTED", 10, 5));
        assertEquals(List.of("ada", "bob"), rows.stream().map(Scoreboard.Row::username).toList());
        assertEquals(List.of(1, 2), rows.stream().map(Scoreboard.Row::rank).toList());
    }

    @Test
    void identicalResultsShareARank() {
        List<Scoreboard.Row> rows = board(List.of("cat", "ada", "bob"),
                at("ada", "two-sum", "ACCEPTED", 3, 0),
                at("bob", "two-sum", "ACCEPTED", 3, 0));
        assertEquals(List.of(1, 1, 3), rows.stream().map(Scoreboard.Row::rank).toList());
        assertEquals("cat", rows.get(2).username());
    }

    @Test
    void playersWhoHaveNotSubmittedAreListedWithNothing() {
        List<Scoreboard.Row> rows = board(List.of("ada", "bob"));
        assertEquals(2, rows.size());
        assertEquals(0, rows.get(0).solved());
        assertEquals(2, rows.get(0).cells().size());
    }

    @Test
    void submissionsForOtherProblemsAreIgnored() {
        Scoreboard.Row ada = board(List.of("ada"), at("ada", "shortest-paths", "ACCEPTED", 1, 0)).get(0);
        assertEquals(0, ada.solved());
    }

    @Test
    void penaltyUsesWholeMinutes() {
        Scoreboard.Row ada = board(List.of("ada"), at("ada", "two-sum", "ACCEPTED", 0, 59)).get(0);
        assertEquals(0, ada.penalty());
        assertEquals(0, ada.cells().get(0).solvedAtMinute().longValue());
    }
}
