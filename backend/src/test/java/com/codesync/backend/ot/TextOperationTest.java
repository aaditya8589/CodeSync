package com.codesync.backend.ot;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    // ---------- helpers ----------

    private static String randomText(Random random, int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append((char) ('a' + random.nextInt(26)));
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
}