package com.codesync.backend.ot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An edit to a plain-text document, described as a walk over the old text from start to end.
 *
 * <p>The operation is a sequence of three kinds of component:
 * <ul>
 *   <li>{@link Retain} n: keep the next n characters unchanged</li>
 *   <li>{@link Insert} s: insert s at the current position</li>
 *   <li>{@link Delete} n: remove the next n characters</li>
 * </ul>
 *
 * <p>Example: on "hello world", inserting "big " before "world" is
 * {@code retain(6).insert("big ").retain(5)}.
 *
 * <p>Positions are counted in UTF-16 code units, which is how both Java Strings and
 * JavaScript (and therefore Monaco) count them, so offsets match on both sides.
 *
 * <p>Instances are immutable once built. Build them with the fluent methods, which also keep the
 * component list normalized (no empty components, adjacent components of the same kind merged,
 * and an insert always placed before a delete at the same position).
 */
public final class TextOperation {

    public sealed interface Component permits Retain, Insert, Delete {
    }

    public record Retain(int count) implements Component {
        public Retain {
            if (count <= 0) throw new IllegalArgumentException("retain count must be positive");
        }
    }

    public record Insert(String text) implements Component {
        public Insert {
            if (text == null || text.isEmpty()) throw new IllegalArgumentException("insert text must be non-empty");
        }
    }

    public record Delete(int count) implements Component {
        public Delete {
            if (count <= 0) throw new IllegalArgumentException("delete count must be positive");
        }
    }

    private final List<Component> components = new ArrayList<>();

    // Length of the text this operation can be applied to
    private int baseLength = 0;

    // Length of the text after applying this operation
    private int targetLength = 0;

    public TextOperation retain(int count) {
        if (count < 0) throw new IllegalArgumentException("retain count must not be negative");
        if (count == 0) return this;

        baseLength += count;
        targetLength += count;

        if (lastComponent() instanceof Retain last) {
            replaceLast(new Retain(last.count() + count));
        } else {
            components.add(new Retain(count));
        }
        return this;
    }

    public TextOperation insert(String text) {
        Objects.requireNonNull(text, "text");
        if (text.isEmpty()) return this;

        targetLength += text.length();

        Component last = lastComponent();
        if (last instanceof Insert lastInsert) {
            replaceLast(new Insert(lastInsert.text() + text));
        } else if (last instanceof Delete lastDelete) {
            // Keep the normal form "insert before delete". Both orders mean the same thing,
            // so this makes equal operations look equal.
            Component beforeDelete = components.size() >= 2 ? components.get(components.size() - 2) : null;
            if (beforeDelete instanceof Insert beforeInsert) {
                components.set(components.size() - 2, new Insert(beforeInsert.text() + text));
            } else {
                components.set(components.size() - 1, new Insert(text));
                components.add(lastDelete);
            }
        } else {
            components.add(new Insert(text));
        }
        return this;
    }

    public TextOperation delete(int count) {
        if (count < 0) throw new IllegalArgumentException("delete count must not be negative");
        if (count == 0) return this;

        baseLength += count;

        if (lastComponent() instanceof Delete last) {
            replaceLast(new Delete(last.count() + count));
        } else {
            components.add(new Delete(count));
        }
        return this;
    }

    public List<Component> components() {
        return Collections.unmodifiableList(components);
    }

    public int baseLength() {
        return baseLength;
    }

    public int targetLength() {
        return targetLength;
    }

    /** True if applying this operation leaves any text unchanged. */
    public boolean isNoop() {
        return components.isEmpty() || (components.size() == 1 && components.get(0) instanceof Retain);
    }

    /** Applies this operation to {@code text} and returns the new text. */
    public String apply(String text) {
        if (text.length() != baseLength) {
            throw new IllegalArgumentException(
                    "Operation expects text of length " + baseLength + " but got " + text.length());
        }

        StringBuilder result = new StringBuilder(targetLength);
        int position = 0;

        for (Component component : components) {
            switch (component) {
                case Retain r -> {
                    result.append(text, position, position + r.count());
                    position += r.count();
                }
                case Insert i -> result.append(i.text());
                case Delete d -> position += d.count();
            }
        }

        return result.toString();
    }

    /**
     * Combines two consecutive operations into one: applying the result equals applying
     * {@code first} and then {@code second}. Used to merge several buffered edits into one.
     */
    public static TextOperation compose(TextOperation first, TextOperation second) {
        if (first.targetLength != second.baseLength) {
            throw new IllegalArgumentException(
                    "Cannot compose: first produces length " + first.targetLength
                            + " but second expects " + second.baseLength);
        }

        TextOperation result = new TextOperation();
        Cursor a = new Cursor(first.components);
        Cursor b = new Cursor(second.components);

        while (a.hasNext() || b.hasNext()) {
            // first's deletes act on text second never sees
            if (a.peek() instanceof Delete d) {
                result.delete(d.count());
                a.next();
                continue;
            }
            // second's inserts add text first never had
            if (b.peek() instanceof Insert i) {
                result.insert(i.text());
                b.next();
                continue;
            }

            if (!a.hasNext() || !b.hasNext()) {
                throw new IllegalStateException("Operations do not line up while composing");
            }

            Component ca = a.peek();
            Component cb = b.peek();

            if (ca instanceof Retain ra && cb instanceof Retain rb) {
                int n = Math.min(ra.count(), rb.count());
                result.retain(n);
                a.consume(n);
                b.consume(n);
            } else if (ca instanceof Insert ia && cb instanceof Delete db) {
                // second deletes text that first inserted: they cancel out
                int n = Math.min(ia.text().length(), db.count());
                a.consume(n);
                b.consume(n);
            } else if (ca instanceof Insert ia && cb instanceof Retain rb) {
                int n = Math.min(ia.text().length(), rb.count());
                result.insert(ia.text().substring(0, n));
                a.consume(n);
                b.consume(n);
            } else if (ca instanceof Retain ra && cb instanceof Delete db) {
                int n = Math.min(ra.count(), db.count());
                result.delete(n);
                a.consume(n);
                b.consume(n);
            } else {
                throw new IllegalStateException("Unexpected components while composing: " + ca + ", " + cb);
            }
        }

        return result;
    }

    /**
     * The core of OT. Given two operations {@code a} and {@code b} made concurrently on the same
     * text, returns {@code [a', b']} such that
     *
     * <pre>  apply(apply(text, a), b') == apply(apply(text, b), a')</pre>
     *
     * <p>When both insert at the same position, {@code a}'s text is placed first. The server
     * always passes the operation it already applied as {@code a}, so every client breaks ties
     * the same way and all copies converge.
     */
    public static TextOperation[] transform(TextOperation a, TextOperation b) {
        if (a.baseLength != b.baseLength) {
            throw new IllegalArgumentException(
                    "Cannot transform: operations apply to different lengths "
                            + a.baseLength + " and " + b.baseLength);
        }

        TextOperation aPrime = new TextOperation();
        TextOperation bPrime = new TextOperation();
        Cursor ca = new Cursor(a.components);
        Cursor cb = new Cursor(b.components);

        while (ca.hasNext() || cb.hasNext()) {
            // a inserts: b' must skip over the new text
            if (ca.peek() instanceof Insert i) {
                aPrime.insert(i.text());
                bPrime.retain(i.text().length());
                ca.next();
                continue;
            }
            // b inserts: a' must skip over the new text
            if (cb.peek() instanceof Insert i) {
                aPrime.retain(i.text().length());
                bPrime.insert(i.text());
                cb.next();
                continue;
            }

            if (!ca.hasNext() || !cb.hasNext()) {
                throw new IllegalStateException("Operations do not line up while transforming");
            }

            Component x = ca.peek();
            Component y = cb.peek();

            if (x instanceof Retain rx && y instanceof Retain ry) {
                int n = Math.min(rx.count(), ry.count());
                aPrime.retain(n);
                bPrime.retain(n);
                ca.consume(n);
                cb.consume(n);
            } else if (x instanceof Delete dx && y instanceof Delete dy) {
                // both deleted the same text: nothing left to do for either
                int n = Math.min(dx.count(), dy.count());
                ca.consume(n);
                cb.consume(n);
            } else if (x instanceof Delete dx && y instanceof Retain ry) {
                // a deleted text b kept: a' still deletes it, b' has nothing to keep
                int n = Math.min(dx.count(), ry.count());
                aPrime.delete(n);
                ca.consume(n);
                cb.consume(n);
            } else if (x instanceof Retain rx && y instanceof Delete dy) {
                int n = Math.min(rx.count(), dy.count());
                bPrime.delete(n);
                ca.consume(n);
                cb.consume(n);
            } else {
                throw new IllegalStateException("Unexpected components while transforming: " + x + ", " + y);
            }
        }

        return new TextOperation[]{aPrime, bPrime};
    }

    /**
     * Rebases an operation made against an older revision onto the current one, by transforming it
     * past every operation the server applied since. {@code concurrent} must be in revision order.
     */
    public static TextOperation rebase(TextOperation operation, List<TextOperation> concurrent) {
        TextOperation result = operation;
        for (TextOperation applied : concurrent) {
            result = transform(applied, result)[1];
        }
        return result;
    }

    /**
     * An operation that turns {@code from} into {@code to}: keep the common start and end,
     * replace the middle. Not a minimal diff, but a single edit is all a restore needs, and it
     * keeps the operation small when only part of the file changed. Never splits an emoji.
     */
    public static TextOperation between(String from, String to) {
        int maxCommon = Math.min(from.length(), to.length());

        int prefix = 0;
        while (prefix < maxCommon && from.charAt(prefix) == to.charAt(prefix)) prefix++;
        if (prefix > 0 && Character.isHighSurrogate(from.charAt(prefix - 1))) prefix--;

        int suffix = 0;
        while (suffix < maxCommon - prefix
                && from.charAt(from.length() - 1 - suffix) == to.charAt(to.length() - 1 - suffix)) {
            suffix++;
        }
        if (suffix > 0 && Character.isLowSurrogate(from.charAt(from.length() - suffix))) suffix--;

        return new TextOperation()
                .retain(prefix)
                .delete(from.length() - prefix - suffix)
                .insert(to.substring(prefix, to.length() - suffix))
                .retain(suffix);
    }

    /**
     * True if the text has no half of a surrogate pair on its own. Such text cannot be stored as
     * UTF-8, and an operation positioned inside an emoji would produce it.
     */
    public static boolean isWellFormedUtf16(String text) {
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            if (Character.isHighSurrogate(c)) {
                if (index + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(index + 1))) {
                    return false;
                }
                index++;
            } else if (Character.isLowSurrogate(c)) {
                return false;
            }
        }
        return true;
    }

    /** Wire format: positive number = retain, string = insert, negative number = delete. */
    public List<Object> toJson() {
        List<Object> json = new ArrayList<>(components.size());
        for (Component component : components) {
            switch (component) {
                case Retain r -> json.add(r.count());
                case Insert i -> json.add(i.text());
                case Delete d -> json.add(-d.count());
            }
        }
        return json;
    }

    public static TextOperation fromJson(List<?> json) {
        if (json == null) throw new IllegalArgumentException("operation is missing");

        TextOperation operation = new TextOperation();
        for (Object item : json) {
            if (item instanceof String text) {
                if (text.isEmpty()) throw new IllegalArgumentException("empty insert in operation");
                operation.insert(text);
            } else if (item instanceof Integer || item instanceof Long) {
                long value = ((Number) item).longValue();
                if (value == 0 || Math.abs(value) > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException("invalid retain/delete count: " + value);
                }
                if (value > 0) operation.retain((int) value);
                else operation.delete((int) -value);
            } else {
                throw new IllegalArgumentException("invalid operation component: " + item);
            }
        }
        return operation;
    }

    /** Compact JSON text for storing in the database, e.g. [12,"x",-3,40]. */
    public String toJsonString() {
        StringBuilder out = new StringBuilder("[");
        for (int index = 0; index < components.size(); index++) {
            if (index > 0) out.append(',');
            switch (components.get(index)) {
                case Retain r -> out.append(r.count());
                case Delete d -> out.append(-d.count());
                case Insert i -> appendJsonString(out, i.text());
            }
        }
        return out.append(']').toString();
    }

    public static TextOperation fromJsonString(String json) {
        return fromJson(new JsonArrayParser(json).parse());
    }

    private static void appendJsonString(StringBuilder out, String text) {
        out.append('"');
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TextOperation op && components.equals(op.components);
    }

    @Override
    public int hashCode() {
        return components.hashCode();
    }

    @Override
    public String toString() {
        return components.toString();
    }

    private Component lastComponent() {
        return components.isEmpty() ? null : components.get(components.size() - 1);
    }

    private void replaceLast(Component component) {
        components.set(components.size() - 1, component);
    }

    /** Parses the stored format only: a flat JSON array of integers and strings. */
    private static final class JsonArrayParser {

        private final String json;
        private int position = 0;

        JsonArrayParser(String json) {
            if (json == null) throw new IllegalArgumentException("operation JSON is missing");
            this.json = json;
        }

        List<Object> parse() {
            List<Object> items = new ArrayList<>();
            skipWhitespace();
            expect('[');
            skipWhitespace();

            if (peek() == ']') {
                position++;
            } else {
                while (true) {
                    skipWhitespace();
                    items.add(peek() == '"' ? readString() : readInteger());
                    skipWhitespace();
                    char c = next();
                    if (c == ']') break;
                    if (c != ',') throw error("expected ',' or ']'");
                }
            }

            skipWhitespace();
            if (position != json.length()) throw error("unexpected trailing characters");
            return items;
        }

        private Long readInteger() {
            int start = position;
            if (peek() == '-') position++;
            while (position < json.length() && Character.isDigit(json.charAt(position))) position++;
            try {
                return Long.parseLong(json.substring(start, position));
            } catch (NumberFormatException exception) {
                throw error("invalid number");
            }
        }

        private String readString() {
            expect('"');
            StringBuilder text = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') return text.toString();
                if (c != '\\') {
                    text.append(c);
                    continue;
                }
                char escaped = next();
                switch (escaped) {
                    case '"' -> text.append('"');
                    case '\\' -> text.append('\\');
                    case '/' -> text.append('/');
                    case 'b' -> text.append('\b');
                    case 'f' -> text.append('\f');
                    case 'n' -> text.append('\n');
                    case 'r' -> text.append('\r');
                    case 't' -> text.append('\t');
                    case 'u' -> {
                        if (position + 4 > json.length()) throw error("truncated \\u escape");
                        try {
                            text.append((char) Integer.parseInt(json.substring(position, position + 4), 16));
                        } catch (NumberFormatException exception) {
                            throw error("invalid \\u escape");
                        }
                        position += 4;
                    }
                    default -> throw error("invalid escape");
                }
            }
        }

        private void skipWhitespace() {
            while (position < json.length() && Character.isWhitespace(json.charAt(position))) position++;
        }

        private char peek() {
            if (position >= json.length()) throw error("unexpected end of input");
            return json.charAt(position);
        }

        private char next() {
            char c = peek();
            position++;
            return c;
        }

        private void expect(char expected) {
            if (next() != expected) throw error("expected '" + expected + "'");
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException("Invalid operation JSON at " + position + ": " + message);
        }
    }

    /**
     * Walks a component list, allowing part of a component to be consumed
     * (for example 3 characters of a retain of 10).
     */
    private static final class Cursor {

        private final List<Component> components;
        private int index = 0;
        private Component current;

        Cursor(List<Component> components) {
            this.components = components;
            this.current = components.isEmpty() ? null : components.get(0);
        }

        boolean hasNext() {
            return current != null;
        }

        Component peek() {
            return current;
        }

        void next() {
            index++;
            current = index < components.size() ? components.get(index) : null;
        }

        /** Consumes n characters of the current component. */
        void consume(int n) {
            int length = switch (current) {
                case Retain r -> r.count();
                case Insert i -> i.text().length();
                case Delete d -> d.count();
            };

            if (n > length) throw new IllegalStateException("Consuming more than the component holds");

            if (n == length) {
                next();
            } else {
                current = switch (current) {
                    case Retain r -> new Retain(r.count() - n);
                    case Insert i -> new Insert(i.text().substring(n));
                    case Delete d -> new Delete(d.count() - n);
                };
            }
        }
    }
}