package com.codesync.backend.dto.arena;

import java.util.List;

/** Everything a solver may see: the statement and the example tests, never the hidden ones. */
public record ProblemDetail(
        String slug,
        String title,
        String difficulty,
        String statement,
        String inputFormat,
        String outputFormat,
        String constraints,
        List<Example> examples,
        int testCount,
        boolean solved
) {
    public record Example(String input, String output) {
    }
}
