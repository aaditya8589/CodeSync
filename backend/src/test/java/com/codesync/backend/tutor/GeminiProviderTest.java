package com.codesync.backend.tutor;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Against a local server that answers like the Gemini API. */
class GeminiProviderTest {

    private HttpServer server;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> key = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();

    private GeminiProvider serve(int status, String response) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().toString());
            key.set(exchange.getRequestHeaders().getFirst("x-goog-api-key"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", status == 200 ? "text/event-stream" : "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        return new GeminiProvider("http://127.0.0.1:" + server.getAddress().getPort() + "/", "test-key",
                "gemini-test", Duration.ofSeconds(5));
    }

    @AfterEach
    void stop() {
        if (server != null) server.stop(0);
    }

    private static String chunk(String text) {
        return "data: {\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":" + Json.quote(text) + "}]}}]}\r\n\r\n";
    }

    @Test
    void streamsTextPiecesAndSendsTheConversation() throws IOException {
        GeminiProvider provider = serve(200, chunk("Think about ") + chunk("what you've seen.\n")
                + "data: {\"candidates\":[{\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"totalTokenCount\":9}}\r\n\r\n");
        List<String> pieces = new ArrayList<>();
        provider.stream("SYSTEM \"rules\"", List.of(
                new TutorMessage(TutorMessage.Role.STUDENT, "hi"),
                new TutorMessage(TutorMessage.Role.TUTOR, "hello"),
                new TutorMessage(TutorMessage.Role.STUDENT, "hint please")), pieces::add);

        assertEquals(List.of("Think about ", "what you've seen.\n"), pieces);
        assertEquals("/v1beta/models/gemini-test:streamGenerateContent?alt=sse", path.get());
        assertEquals("test-key", key.get());
        Object request = Json.parse(body.get());
        assertEquals("SYSTEM \"rules\"", Json.path(request, "systemInstruction", "parts", 0, "text"));
        assertEquals("user", Json.path(request, "contents", 0, "role"));
        assertEquals("model", Json.path(request, "contents", 1, "role"));
        assertEquals("hint please", Json.path(request, "contents", 2, "parts", 0, "text"));
    }

    @Test
    void quotaAndKeyErrorsBecomeClearMessages() throws IOException {
        GeminiProvider quota = serve(429, "{\"error\":{\"code\":429,\"message\":\"Resource exhausted\"}}");
        assertTrue(assertThrows(TutorProvider.TutorProviderException.class,
                () -> quota.stream("s", List.of(), text -> {})).getMessage().contains("free quota"));
        stop();

        GeminiProvider badKey = serve(400, "{\"error\":{\"code\":400,\"message\":\"API key not valid. Please pass a valid API key.\"}}");
        assertTrue(assertThrows(TutorProvider.TutorProviderException.class,
                () -> badKey.stream("s", List.of(), text -> {})).getMessage().contains("GEMINI_API_KEY"));
        stop();

        GeminiProvider noModel = serve(404, "{\"error\":{\"code\":404,\"message\":\"models/gemini-test is not found\"}}");
        assertTrue(assertThrows(TutorProvider.TutorProviderException.class,
                () -> noModel.stream("s", List.of(), text -> {})).getMessage().contains("TUTOR_MODEL"));
    }

    @Test
    void aBlockedOrEmptyAnswerIsAnError() throws IOException {
        GeminiProvider provider = serve(200, "data: {\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}\r\n\r\n");
        assertThrows(TutorProvider.TutorProviderException.class, () -> provider.stream("s", List.of(), text -> {}));
    }

    @Test
    void withoutAKeyItIsSwitchedOff() {
        assertFalse(new GeminiProvider("http://x", "", "m", Duration.ofSeconds(1)).isConfigured());
        assertFalse(new GeminiProvider("http://x", "<your key>", "m", Duration.ofSeconds(1)).isConfigured());
        assertTrue(new GeminiProvider("http://x", "AIza123", "m", Duration.ofSeconds(1)).isConfigured());
    }

    @Test
    void whenTheReceiverFailsTheErrorIsPassedOnAsItIs() throws IOException {
        GeminiProvider provider = serve(200, chunk("one") + chunk("two"));
        IllegalStateException gone = assertThrows(IllegalStateException.class,
                () -> provider.stream("s", List.of(), text -> { throw new IllegalStateException("browser left"); }));
        assertEquals("browser left", gone.getMessage());
    }
}
