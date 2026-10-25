package com.codesync.backend.tutor;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonTest {

    @Test
    void quotedTextRoundTrips() {
        String text = "line \"one\"\n\ttab \\ back\u0001 – ünïcode 🙂";
        assertEquals(text, Json.parse(Json.quote(text)));
    }

    @Test
    void readsNestedValues() {
        Object json = Json.parse("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hi\\u0021\"}]}}],"
                + "\"n\":12,\"x\":-1.5e2,\"ok\":true,\"none\":null,\"empty\":{},\"list\":[]}");
        assertEquals("Hi!", Json.path(json, "candidates", 0, "content", "parts", 0, "text"));
        assertEquals(12L, Json.path(json, "n"));
        assertEquals(-150.0, Json.path(json, "x"));
        assertEquals(Boolean.TRUE, Json.path(json, "ok"));
        assertNull(Json.path(json, "none"));
        assertEquals(Map.of(), Json.path(json, "empty"));
        assertEquals(List.of(), Json.path(json, "list"));
        assertNull(Json.path(json, "candidates", 3, "content"));
        assertNull(Json.path(json, "n", "deeper"));
    }

    @Test
    void rejectsBrokenJson() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("{\"a\":1} extra"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("\"open"));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("[1,]"));
    }
}
