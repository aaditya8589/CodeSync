package com.codesync.backend.tutor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Just enough JSON for talking to an AI provider: quoting strings for requests, and reading
 * responses into maps, lists, strings, numbers, booleans and null. Kept here rather than tied
 * to one Jackson version, since only a few small messages pass through it.
 */
final class Json {

    private final String text;
    private int position;

    private Json(String text) {
        this.text = text;
    }

    static String quote(String value) {
        StringBuilder out = new StringBuilder(value.length() + 16).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
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
        return out.append('"').toString();
    }

    /** @throws IllegalArgumentException if the text is not one JSON value */
    static Object parse(String text) {
        Json parser = new Json(text);
        Object value = parser.value();
        parser.skipSpace();
        if (parser.position != text.length()) throw parser.error("Unexpected text after the value");
        return value;
    }

    /** Follows keys and list indexes, e.g. path(json, "candidates", 0, "content"); null if absent. */
    static Object path(Object value, Object... steps) {
        for (Object step : steps) {
            if (step instanceof String key && value instanceof Map<?, ?> map) {
                value = map.get(key);
            } else if (step instanceof Integer index && value instanceof List<?> list && index < list.size()) {
                value = list.get(index);
            } else {
                return null;
            }
        }
        return value;
    }

    private Object value() {
        skipSpace();
        if (position >= text.length()) throw error("Unexpected end");
        char c = text.charAt(position);
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        position++;
        skipSpace();
        if (peek('}')) return map;
        while (true) {
            skipSpace();
            if (position >= text.length() || text.charAt(position) != '"') throw error("Expected a key");
            String key = string();
            skipSpace();
            expect(':');
            map.put(key, value());
            skipSpace();
            if (peek('}')) return map;
            expect(',');
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        position++;
        skipSpace();
        if (peek(']')) return list;
        while (true) {
            list.add(value());
            skipSpace();
            if (peek(']')) return list;
            expect(',');
        }
    }

    private String string() {
        position++;
        StringBuilder out = new StringBuilder();
        while (position < text.length()) {
            char c = text.charAt(position++);
            if (c == '"') return out.toString();
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (position >= text.length()) break;
            char e = text.charAt(position++);
            switch (e) {
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'u' -> {
                    if (position + 4 > text.length()) throw error("Bad escape");
                    out.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                    position += 4;
                }
                default -> out.append(e);
            }
        }
        throw error("Unterminated string");
    }

    private Object number() {
        int start = position;
        while (position < text.length() && "+-0123456789.eE".indexOf(text.charAt(position)) >= 0) position++;
        if (start == position) throw error("Unexpected character");
        String number = text.substring(start, position);
        try {
            return number.contains(".") || number.contains("e") || number.contains("E")
                    ? (Object) Double.parseDouble(number)
                    : (Object) Long.parseLong(number);
        } catch (NumberFormatException exception) {
            throw error("Bad number");
        }
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, position)) throw error("Unexpected word");
        position += word.length();
        return value;
    }

    private boolean peek(char c) {
        if (position < text.length() && text.charAt(position) == c) {
            position++;
            return true;
        }
        return false;
    }

    private void expect(char c) {
        if (!peek(c)) throw error("Expected '" + c + "'");
    }

    private void skipSpace() {
        while (position < text.length() && Character.isWhitespace(text.charAt(position))) position++;
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at " + position);
    }
}
