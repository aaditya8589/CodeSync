package com.codesync.backend.ot;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextOperationTest {

    // Fixed seed: if a random test ever fails, it fails the same way every run
    private static final long SEED = 42L;
    private static final int RANDOM_RUNS = 10_000;

    // ---------- apply ----------

    @Test
    void applyInsertsInTheMiddle() {
        TextOperation op = new TextOperation().retain(6).insert("big ").retain(5);
        assertEquals("hello big world", op.apply("hello world"));
    }

    @Test
    void applyDeletes() {
        TextOperation op = new TextOperation().retain(5).delete(6);
        assertEquals("hello", op.apply("hello world"));
    }

    @Test
    void applyReplaces() {
        TextOperation op = new TextOperation().retain(6).delete(5).insert("there");
        assertEquals("hello there", op.apply("hello world"));
    }

    @Test
    void applyRejectsTextOfWrongLength() {
        TextOperation op = new TextOperation().retain(3);
        assertThrows(IllegalArgumentException.class, () -> op.apply("too long"));
    }

    @Test
    void lengthsAreTracked() {
        TextOperation op = new TextOperation().retain(2).insert("abc").delete(4);
        assertEquals(6, op.baseLength());
        assertEquals(5, op.targetLength());
    }

    // ---------- normal form ----------

    @Test
    void adjacentComponentsOfSameKindAreMerged() {
        TextOperation op = new TextOperation().retain(1).retain(2).insert("a").insert("b").delete(1).delete(1);
        assertEquals(new TextOperation().retain(3).insert("ab").delete(2), op);
    }

    @Test
    void insertIsPlacedBeforeDeleteAtSamePosition() {
        TextOperation deleteThenInsert = new TextOperation().delete(2).insert("x");
        TextOperation insertThenDelete = new TextOperation().insert("x").delete(2);
        assertEquals(insertThenDelete, deleteThenInsert);
    }

    // ---------- transform: hand-checked cases ----------

    @Test
    void transformConcurrentInsertsAtDifferentPositions() {
        String text = "abc";
        TextOperation a = new TextOperation().insert("X").retain(3);  // "Xabc"
        TextOperation b = new TextOperation().retain(3).insert("Y");  // "abcY"

        TextOperation[] t = TextOperation.transform(a, b);

        assertEquals("XabcY", t[1].apply(a.apply(text)));
        assertEquals("XabcY", t[0].apply(b.apply(text)));
    }

    @Test
    void transformTieBreakPutsFirstOperationFirst() {
        String text = "ab";
        TextOperation a = new TextOperation().retain(1).insert("A").retain(1);
        TextOperation b = new TextOperation().retain(1).insert("B").retain(1);

        TextOperation[] t = TextOperation.transform(a, b);

        assertEquals("aABb", t[1].apply(a.apply(text)));
        assertEquals("aABb", t[0].apply(b.apply(text)));
    }

    @Test
    void transformInsertInsideTextTheOtherDeleted() {
        String text = "hello world";
        TextOperation a = new TextOperation().retain(5).delete(6);                 // "hello"
        TextOperation b = new TextOperation().retain(8).insert("!!").retain(3);    // "hello wo!!rld"

        TextOperation[] t = TextOperation.transform(a, b);

        String viaA = t[1].apply(a.apply(text));
        String viaB = t[0].apply(b.apply(text));
        assertEquals(viaA, viaB);
        assertEquals("hello!!", viaA); // the insert survives, the deleted text does not
    }

    @Test
    void transformOverlappingDeletes() {
        String text = "abcdef";
        TextOperation a = new TextOperation().retain(1).delete(3).retain(2);  // deletes "bcd"
        TextOperation b = new TextOperation().retain(2).delete(3).retain(1);  // deletes "cde"

        TextOperation[] t = TextOperation.transform(a, b);

        assertEquals("af", t[1].apply(a.apply(text)));
        assertEquals("af", t[0].apply(b.apply(text)));
    }

    @Test
    void transformRejectsOperationsOnDifferentLengths() {
        TextOperation a = new TextOperation().retain(3);
        TextOperation b = new TextOperation().retain(4);
        assertThrows(IllegalArgumentException.class, () -> TextOperation.transform(a, b));
    }

    // ---------- randomized properties ----------

    /** The OT convergence property: both orders of applying two concurrent edits agree. */
    @Test
    void randomConcurrentEditsAlwaysConverge() {
        Random random = new Random(SEED);

        for (int run = 0; run < RANDOM_RUNS; run++) {
            String text = randomText(random, random.nextInt(20));
            TextOperation a = randomOperation(random, text);
            TextOperation b = randomOperation(random, text);

            TextOperation[] t = TextOperation.transform(a, b);

            String viaA = t[1].apply(a.apply(text));
            String viaB = t[0].apply(b.apply(text));

            assertEquals(viaA, viaB,
                    () -> "Diverged on text=\"" + text + "\" a=" + a + " b=" + b);
        }
    }

    @Test
    void randomComposeEqualsApplyingInSequence() {
        Random random = new Random(SEED);

        for (int run = 0; run < RANDOM_RUNS; run++) {
            String text = randomText(random, random.nextInt(20));
            TextOperation first = randomOperation(random, text);
            String middle = first.apply(text);
            TextOperation second = randomOperation(random, middle);

            TextOperation composed = TextOperation.compose(first, second);

            assertEquals(second.apply(middle), composed.apply(text),
                    () -> "Compose wrong on text=\"" + text + "\" first=" + first + " second=" + second);
        }
    }

    @Test
    void randomOperationsKeepLengthsConsistent() {
        Random random = new Random(SEED);

        for (int run = 0; run < RANDOM_RUNS; run++) {
            String text = randomText(random, random.nextInt(20));
            TextOperation op = randomOperation(random, text);

            assertEquals(text.length(), op.baseLength());
            assertEquals(op.targetLength(), op.apply(text).length());
            assertTrue(op.baseLength() >= 0 && op.targetLength() >= 0);
        }
    }

    // ---------- JSON ----------

    @Test
    void toJsonUsesWireFormat() {
        TextOperation op = new TextOperation().retain(3).insert("hi").delete(2).retain(1);
        assertEquals(List.of(3, "hi", -2, 1), op.toJson());
        assertEquals("[3,\"hi\",-2,1]", op.toJsonString());
    }

    @Test
    void fromJsonAcceptsIntegerAndLongNumbers() {
        TextOperation expected = new TextOperation().retain(3).insert("x").delete(2);
        assertEquals(expected, TextOperation.fromJson(List.of(3, "x", -2)));
        assertEquals(expected, TextOperation.fromJson(List.of(3L, "x", -2L)));
    }

    @Test
    void fromJsonRejectsInvalidComponents() {
        assertThrows(IllegalArgumentException.class, () -> TextOperation.fromJson(null));
        assertThrows(IllegalArgumentException.class, () -> TextOperation.fromJson(List.of(0)));
        assertThrows(IllegalArgumentException.class, () -> TextOperation.fromJson(List.of("")));
        assertThrows(IllegalArgumentException.class, () -> TextOperation.fromJson(List.of(1.5)));
        assertThrows(IllegalArgumentException.class, () -> TextOperation.fromJson(List.of(true)));
        assertThrows(IllegalArgumentException.class, () -> TextOperation.fromJson(List.of(1L + Integer.MAX_VALUE)));
    }

    @Test
    void fromJsonStringRejectsMalformedInput() {
        for (String bad : new String[]{"", "[", "[1,]", "[1 2]", "[\"abc]", "[1]x", "{}", "[1.5]", "[-]", "[\"\\q\"]", "[\"\\u12\"]"}) {
            assertThrows(IllegalArgumentException.class, () -> TextOperation.fromJsonString(bad));
        }
    }

    @Test
    void randomJsonStringRoundTripsAnyText() {
        Random random = new Random(SEED);

        for (int run = 0; run < RANDOM_RUNS; run++) {
            TextOperation op = new TextOperation();
            int parts = 1 + random.nextInt(5);
            for (int i = 0; i < parts; i++) {
                switch (random.nextInt(3)) {
                    case 0 -> op.retain(1 + random.nextInt(1000));
                    case 1 -> op.delete(1 + random.nextInt(1000));
                    default -> op.insert(randomUnicode(random, 1 + random.nextInt(8)));
                }
            }

            String json = op.toJsonString();
            assertEquals(op, TextOperation.fromJsonString(json), () -> "Round trip failed for " + json);
        }
    }

    // ---------- rebase ----------

    /**
     * A client's op made at an old revision is rebased past several ops the server applied since.
     * The result must equal what the client sees: its own op first, then each server op transformed.
     */
    @Test
    void randomRebaseOverSeveralOperationsConverges() {
        Random random = new Random(SEED);

        for (int run = 0; run < RANDOM_RUNS; run++) {
            String base = randomText(random, random.nextInt(20));

            List<TextOperation> history = new ArrayList<>();
            String serverText = base;
            int historyLength = random.nextInt(5);
            for (int i = 0; i < historyLength; i++) {
                TextOperation applied = randomOperation(random, serverText);
                history.add(applied);
                serverText = applied.apply(serverText);
            }

            TextOperation clientOp = randomOperation(random, base);

            String serverResult = TextOperation.rebase(clientOp, history).apply(serverText);

            String clientText = clientOp.apply(base);
            TextOperation pending = clientOp;
            for (TextOperation applied : history) {
                TextOperation[] t = TextOperation.transform(applied, pending);
                clientText = t[0].apply(clientText);
                pending = t[1];
            }

            assertEquals(serverResult, clientText, () -> "Rebase diverged from base=\"" + base + "\"");
        }
    }

    @Test
    void rebaseWithNoHistoryReturnsSameOperation() {
        TextOperation op = new TextOperation().retain(2).insert("x");
        assertEquals(op, TextOperation.rebase(op, List.of()));
    }

    // ---------- UTF-16 ----------

    @Test
    void wellFormedUtf16AcceptsNormalTextAndEmoji() {
        assertTrue(TextOperation.isWellFormedUtf16(""));
        assertTrue(TextOperation.isWellFormedUtf16("int main() { return 0; }"));
        assertTrue(TextOperation.isWellFormedUtf16("caf\u00e9 \u4e2d \ud83d\ude00"));
    }

    @Test
    void wellFormedUtf16RejectsHalfAnEmoji() {
        assertFalse(TextOperation.isWellFormedUtf16("\ud83d"));
        assertFalse(TextOperation.isWellFormedUtf16("\ude00"));
        assertFalse(TextOperation.isWellFormedUtf16("a\ud83dx\ude00"));
        assertFalse(TextOperation.isWellFormedUtf16("\ude00\ud83d"));
    }

    @Test
    void operationInsideAnEmojiProducesMalformedText() {
        String text = "\ud83d\ude00";
        TextOperation splitsEmoji = new TextOperation().retain(1).insert("x").retain(1);
        assertFalse(TextOperation.isWellFormedUtf16(splitsEmoji.apply(text)));
    }

    // ---------- helpers ----------

    private static String randomText(Random random, int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append((char) ('a' + random.nextInt(26)));
        }
        return builder.toString();
    }

    /** Any UTF-16 char, including quotes, backslashes, control characters and surrogate halves. */
    private static String randomUnicode(Random random, int length) {
        String specials = "\"\\/\n\r\t\b\f\u0000\u001f\u007f\u00e9\u4e2d\ud83d\ude00";
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(random.nextBoolean()
                    ? specials.charAt(random.nextInt(specials.length()))
                    : (char) random.nextInt(0x10000));
        }
        return builder.toString();
    }

    /** Builds a random valid operation that covers all of {@code text}. */
    private static TextOperation randomOperation(Random random, String text) {
        TextOperation op = new TextOperation();
        int remaining = text.length();

        while (remaining > 0) {
            int chunk = 1 + random.nextInt(Math.min(remaining, 5));
            switch (random.nextInt(3)) {
                case 0 -> op.retain(chunk);
                case 1 -> op.delete(chunk);
                default -> op.insert(randomText(random, 1 + random.nextInt(3)));
            }
            // inserts do not consume the old text
            if (op.baseLength() > text.length() - remaining) {
                remaining = text.length() - op.baseLength();
            }
        }

        if (random.nextInt(2) == 0) {
            op.insert(randomText(random, 1 + random.nextInt(3)));
        }

        return op;
    }

    // ---------- between ----------

    @Test
    void betweenTurnsOneTextIntoTheOther() {
        TextOperation op = TextOperation.between("int main() { return 0; }", "int main() { return 1; }");
        assertEquals("int main() { return 1; }", op.apply("int main() { return 0; }"));
        // Only the changed character is replaced
        assertEquals(List.of(20, "1", -1, 3), op.toJson());
    }

    @Test
    void betweenIdenticalTextsIsANoop() {
        assertTrue(TextOperation.between("same", "same").isNoop());
        assertTrue(TextOperation.between("", "").isNoop());
    }

    @Test
    void betweenNeverSplitsASurrogatePair() {
        // Both emoji share the same high surrogate, so a naive common prefix would end mid-character
        String from = "a\uD83D\uDE00b";
        String to = "a\uD83D\uDE01b";
        TextOperation op = TextOperation.between(from, to);
        assertEquals(to, op.apply(from));
        assertEquals(List.of(1, "\uD83D\uDE01", -2, 1), op.toJson());

        // Same low surrogate, different high surrogate: a naive common suffix would split it
        String from2 = "x\uD83D\uDE00";
        String to2 = "x\uD83E\uDE00";
        TextOperation op2 = TextOperation.between(from2, to2);
        assertEquals(to2, op2.apply(from2));
        assertEquals(List.of(1, "\uD83E\uDE00", -2), op2.toJson());
    }

    @Test
    void betweenRandomTexts() {
        Random random = new Random(SEED);
        String alphabet = "ab\n\uD83D\uDE00\uD83D\uDE01";
        for (int run = 0; run < RANDOM_RUNS; run++) {
            String from = randomWellFormed(random, alphabet, random.nextInt(12));
            String to = randomWellFormed(random, alphabet, random.nextInt(12));
            TextOperation op = TextOperation.between(from, to);
            assertEquals(to, op.apply(from), from + " -> " + to);
            for (Object part : op.toJson()) {
                if (part instanceof String inserted) assertTrue(TextOperation.isWellFormedUtf16(inserted));
            }
        }
    }

    // Picks whole characters (an emoji is two chars), so the text never has half a pair
    private static String randomWellFormed(Random random, String alphabet, int length) {
        StringBuilder out = new StringBuilder();
        int[] codePoints = alphabet.codePoints().toArray();
        for (int i = 0; i < length; i++) out.appendCodePoint(codePoints[random.nextInt(codePoints.length)]);
        return out.toString();
    }
}
