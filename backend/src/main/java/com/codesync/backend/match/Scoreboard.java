package com.codesync.backend.match;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ICPC-style standings. More problems solved ranks higher; on a tie, less penalty does. Penalty
 * is, for each solved problem, the minutes from the start to the accepted submission plus
 * {@value #PENALTY_MINUTES} minutes for each rejected submission before it. A compile error is
 * not counted against anyone (the code never ran), and neither is a judge failure.
 */
public final class Scoreboard {

    public static final int PENALTY_MINUTES = 5;
    private static final Set<String> NOT_COUNTED = Set.of("COMPILE_ERROR", "INTERNAL_ERROR");

    public record Attempt(String username, String problemSlug, String verdict, Instant at) {
    }

    /** One player on one problem. solvedAtMinute is null while unsolved. */
    public record Cell(String problemSlug, boolean solved, int rejected, Long solvedAtMinute) {
    }

    public record Row(int rank, String username, int solved, long penalty, List<Cell> cells) {
    }

    private Scoreboard() {
    }

    public static List<Row> compute(List<String> problemSlugs, List<String> players,
                                    List<Attempt> attempts, Instant startedAt) {
        // Everyone who joined, plus anyone who submitted (they joined; the list may be read mid-join)
        Set<String> everyone = new LinkedHashSet<>(players);
        attempts.forEach(attempt -> everyone.add(attempt.username()));

        List<Attempt> inOrder = attempts.stream().sorted(Comparator.comparing(Attempt::at)).toList();

        record Standing(String username, int solved, long penalty, long lastSolveSeconds, List<Cell> cells) {
        }
        List<Standing> standings = new ArrayList<>();

        for (String username : everyone) {
            Map<String, Integer> rejected = new HashMap<>();
            Map<String, Long> solvedAtSeconds = new HashMap<>();

            for (Attempt attempt : inOrder) {
                String slug = attempt.problemSlug();
                if (!attempt.username().equals(username) || !problemSlugs.contains(slug)
                        || solvedAtSeconds.containsKey(slug) || NOT_COUNTED.contains(attempt.verdict())) {
                    continue;
                }
                if ("ACCEPTED".equals(attempt.verdict())) {
                    solvedAtSeconds.put(slug, Math.max(0, Duration.between(startedAt, attempt.at()).toSeconds()));
                } else {
                    rejected.merge(slug, 1, Integer::sum);
                }
            }

            List<Cell> cells = new ArrayList<>();
            long penalty = 0;
            long lastSolve = 0;
            for (String slug : problemSlugs) {
                Long seconds = solvedAtSeconds.get(slug);
                int wrong = rejected.getOrDefault(slug, 0);
                if (seconds != null) {
                    penalty += seconds / 60 + (long) wrong * PENALTY_MINUTES;
                    lastSolve = Math.max(lastSolve, seconds);
                }
                cells.add(new Cell(slug, seconds != null, wrong, seconds == null ? null : seconds / 60));
            }
            standings.add(new Standing(username, solvedAtSeconds.size(), penalty, lastSolve, cells));
        }

        // Ties on solved and penalty go to whoever reached their score first
        standings.sort(Comparator.comparingInt(Standing::solved).reversed()
                .thenComparingLong(Standing::penalty)
                .thenComparingLong(Standing::lastSolveSeconds)
                .thenComparing(Standing::username));

        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < standings.size(); i++) {
            Standing standing = standings.get(i);
            int rank = i + 1;
            if (i > 0) {
                Standing previous = standings.get(i - 1);
                if (previous.solved() == standing.solved() && previous.penalty() == standing.penalty()
                        && previous.lastSolveSeconds() == standing.lastSolveSeconds()) {
                    rank = rows.get(i - 1).rank();
                }
            }
            rows.add(new Row(rank, standing.username(), standing.solved(), standing.penalty(), standing.cells()));
        }
        return rows;
    }
}
