package com.codesync.backend.tutor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps code out of the tutor's answers, whatever the model decides to write. The prompt asks
 * for ideas only, but a prompt is a request, not a guarantee, so every answer passes through
 * here on the server before anyone sees it.
 *
 * Works line by line on the streamed text: code blocks, and lines that read as code in any of
 * the three languages, are replaced by one short note; long inline code is shortened. Short
 * inline names like `dp[i]` or `O(n log n)` stay, since explaining an idea needs them.
 */
public final class CodeGuard {

    static final String NOTE = "_(code removed: the tutor explains ideas, you write the code)_";

    private static final Pattern CODE_START = Pattern.compile(
            "^(#include|#define|import\\s|from\\s+\\S+\\s+import\\s|using\\s+namespace|package\\s"
                    + "|public\\s|private\\s|protected\\s|static\\s|def\\s+\\w+\\s*\\(|class\\s+\\w+\\s*[:{(]"
                    + "|int\\s+main|return\\b|print\\s*\\(|System\\.out|std::|cout\\s*<<|cin\\s*>>"
                    + "|printf\\s*\\(|scanf\\s*\\(|else\\s*[:{]|}\\s*else|elif\\s.*:$|try\\s*[:{]|except\\b.*:$"
                    + "|while\\s*\\(|for\\s*\\(|if\\s*\\(|switch\\s*\\()");
    // for x in range(n):   while lo < hi:   if seen[x]:
    private static final Pattern PYTHON_BLOCK = Pattern.compile("^(for|while|if|with)\\s.+:$");
    // best = max(best, price - cheapest)   dp[i] += dp[i - 1]
    private static final Pattern ASSIGNMENT = Pattern.compile(
            "^[A-Za-z_][\\w\\[\\]\\.]*\\s*(=|\\+=|-=|\\*=|/=|%=|<<=|>>=)\\s*[^=\\s].*");
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`\\n]+)`");
    private static final String SYMBOLS = "()[]{}=+-*/%<>;&|!";

    private final StringBuilder pending = new StringBuilder();
    private boolean inFence;
    private boolean noteShown;
    private boolean removedAny;

    /** Takes the next piece of the answer; returns what is safe to show so far. */
    public String accept(String chunk) {
        pending.append(chunk);
        StringBuilder out = new StringBuilder();
        int newline;
        while ((newline = pending.indexOf("\n")) >= 0) {
            String line = pending.substring(0, newline);
            pending.delete(0, newline + 1);
            String kept = line(line);
            if (kept != null) out.append(kept).append('\n');
        }
        return out.toString();
    }

    /** The end of the answer: the last line, if it did not end with a newline. */
    public String finish() {
        String last = pending.toString();
        pending.setLength(0);
        if (last.isEmpty()) return "";
        String kept = line(last);
        return kept == null ? "" : kept;
    }

    public boolean removedAny() {
        return removedAny;
    }

    // The line to show (possibly the note instead), or null to show nothing
    private String line(String line) {
        String trimmed = line.strip();
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            inFence = !inFence;
            return remove();
        }
        if (inFence || isCode(line)) {
            return remove();
        }
        if (!trimmed.isEmpty()) noteShown = false;
        return shortenInlineCode(line);
    }

    private String remove() {
        removedAny = true;
        if (noteShown) return null;
        noteShown = true;
        return NOTE;
    }

    static boolean isCode(String line) {
        String trimmed = line.strip();
        if (trimmed.isEmpty()) return false;
        // List markers and quotes do not make a line prose
        String body = trimmed.replaceFirst("^([-*+>]|\\d+[.)])\\s+", "");
        int symbols = symbolCount(body);
        boolean indented = line.startsWith("    ") || line.startsWith("\t");
        if (CODE_START.matcher(body).find() && (symbols >= 1 || indented)) return true;
        if (body.matches("^(#include|import\\s+[\\w.]+|from\\s+[\\w.]+\\s+import\\s+\\w+|using\\s+namespace\\s+\\w+).*")) return true;
        if (PYTHON_BLOCK.matcher(body).matches() && symbols >= 1) return true;
        if (body.endsWith(";") || body.endsWith("{") || body.equals("}") || body.equals("};")) return true;
        if (ASSIGNMENT.matcher(body).matches() && symbols >= 3) return true;
        // Indented like a code block and dense with symbols
        return indented && !body.matches("^[-*+].*") && symbols >= 3 && symbols * 6 >= body.length();
    }

    private static int symbolCount(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (SYMBOLS.indexOf(text.charAt(i)) >= 0) count++;
        }
        return count;
    }

    private String shortenInlineCode(String line) {
        Matcher matcher = INLINE_CODE.matcher(line);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String code = matcher.group(1);
            boolean tooMuch = code.length() > 40 || code.contains(";") || code.contains("{")
                    || code.contains("}") || isCode(code);
            if (tooMuch) removedAny = true;
            matcher.appendReplacement(out, Matcher.quoteReplacement(tooMuch ? "_(code removed)_" : matcher.group()));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
