package com.codesync.backend.arena;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads one problem from its text file (made by backend/problems/generate.py):
 * "key: value" header lines, then sections that start with "=== name". Example and hidden
 * tests are "=== sample" / "=== test" sections whose expected output follows "--- expected".
 */
public record ProblemFile(
        String slug,
        String title,
        String difficulty,
        int position,
        String statement,
        String inputFormat,
        String outputFormat,
        String constraints,
        // The time complexity a good solution has, e.g. "O(n log n)"; null if the file names none
        String expectedComplexity,
        List<Test> tests,
        // Of the whole file, so an unchanged problem is not rewritten into the database
        String contentHash,
        // For the interviewer in a mock interview only: what a good answer looks like, and hints
        // to give one at a time. Never shown with the problem or sent to the tutor.
        String interviewerGuide,
        List<String> hints
) {

    public record Test(boolean sample, String input, String expectedOutput) {
    }

    public static ProblemFile parse(String slug, String text) {
        Map<String, String> header = new LinkedHashMap<>();
        Map<String, String> sections = new LinkedHashMap<>();
        List<Test> tests = new ArrayList<>();
        List<String> hints = new ArrayList<>();

        String[] lines = text.replace("\r\n", "\n").split("\n", -1);
        int i = 0;
        for (; i < lines.length && !lines[i].startsWith("=== "); i++) {
            String line = lines[i];
            if (line.isBlank()) continue;
            int colon = line.indexOf(':');
            if (colon < 0) throw new IllegalArgumentException(slug + ": bad header line: " + line);
            header.put(line.substring(0, colon).strip(), line.substring(colon + 1).strip());
        }

        while (i < lines.length) {
            String name = lines[i].substring(4).strip();
            i++;
            StringBuilder body = new StringBuilder();
            StringBuilder expected = null;
            for (; i < lines.length && !lines[i].startsWith("=== "); i++) {
                if (lines[i].equals("--- expected") && expected == null) {
                    expected = new StringBuilder();
                } else {
                    (expected == null ? body : expected).append(lines[i]).append('\n');
                }
            }

            if (name.equals("hint")) {
                hints.add(body.toString().strip());
            } else if (name.equals("sample") || name.equals("test")) {
                if (expected == null) throw new IllegalArgumentException(slug + ": test without '--- expected'");
                tests.add(new Test(name.equals("sample"), body.toString(), trimFinalNewline(expected.toString())));
            } else {
                sections.put(name, body.toString().strip());
            }
        }

        if (tests.stream().noneMatch(Test::sample)) {
            throw new IllegalArgumentException(slug + ": needs at least one sample");
        }

        return new ProblemFile(
                slug,
                required(header, "title", slug),
                required(header, "difficulty", slug),
                Integer.parseInt(required(header, "position", slug)),
                required(sections, "statement", slug),
                required(sections, "input", slug),
                required(sections, "output", slug),
                sections.getOrDefault("constraints", ""),
                header.get("complexity"),
                List.copyOf(tests),
                sha256(text),
                sections.get("guide"),
                List.copyOf(hints)
        );
    }

    private static String sha256(String text) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    // The file ends every section with a newline; the last one belongs to the file, not the answer
    private static String trimFinalNewline(String text) {
        return text.endsWith("\n\n") ? text.substring(0, text.length() - 1) : text;
    }

    private static String required(Map<String, String> values, String key, String slug) {
        String value = values.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(slug + ": missing " + key);
        return value;
    }
}
