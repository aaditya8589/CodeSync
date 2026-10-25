package com.codesync.backend.tutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Google's Gemini API, streamed with server-sent events. The free tier needs no card; its limits
 * are per Google Cloud project and shown in AI Studio. The key stays on the server.
 */
public class GeminiProvider implements TutorProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiProvider.class);

    static final int MAX_OUTPUT_TOKENS = 1024;

    private final HttpClient http;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final Duration timeout;

    public GeminiProvider(String baseUrl, String apiKey, String model, Duration timeout) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey == null ? "" : apiKey.strip();
        this.model = model.strip();
        this.timeout = timeout;
    }

    @Override
    public String name() {
        return "Gemini";
    }

    @Override
    public boolean isConfigured() {
        return !apiKey.isEmpty() && !apiKey.startsWith("<");
    }

    String requestBody(String system, List<TutorMessage> turns) {
        String contents = turns.stream()
                .map(turn -> "{\"role\":\"" + (turn.role() == TutorMessage.Role.STUDENT ? "user" : "model")
                        + "\",\"parts\":[{\"text\":" + Json.quote(turn.text()) + "}]}")
                .collect(Collectors.joining(","));
        return "{\"systemInstruction\":{\"parts\":[{\"text\":" + Json.quote(system) + "}]},"
                + "\"contents\":[" + contents + "],"
                + "\"generationConfig\":{\"temperature\":0.7,\"maxOutputTokens\":" + MAX_OUTPUT_TOKENS + "}}";
    }

    @Override
    public void stream(String system, List<TutorMessage> turns, Consumer<String> onText) {
        if (!isConfigured()) throw new TutorProviderException("The tutor is not set up on this server.");
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1beta/models/"
                        + URLEncoder.encode(model, StandardCharsets.UTF_8) + ":streamGenerateContent?alt=sse"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(system, turns), StandardCharsets.UTF_8))
                .build();

        HttpResponse<Stream<String>> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofLines());
        } catch (IOException exception) {
            log.warn("Gemini request failed: {}", exception.toString());
            throw new TutorProviderException("The tutor could not be reached. Try again in a moment.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TutorProviderException("The tutor was interrupted.");
        }

        try (Stream<String> lines = response.body()) {
            if (response.statusCode() != 200) {
                String body = lines.collect(Collectors.joining("\n"));
                throw failure(response.statusCode(), body);
            }
            boolean any = false;
            String blocked = null;
            Iterator<String> iterator = lines.iterator();
            while (iterator.hasNext()) {
                String line = iterator.next();
                if (!line.startsWith("data:")) continue;
                Object json;
                try {
                    json = Json.parse(line.substring(5).strip());
                } catch (IllegalArgumentException exception) {
                    log.warn("Unreadable Gemini chunk: {}", exception.getMessage());
                    continue;
                }
                Object reason = Json.path(json, "promptFeedback", "blockReason");
                if (reason != null) blocked = reason.toString();
                if (Json.path(json, "candidates", 0, "content", "parts") instanceof List<?> parts) {
                    for (Object part : parts) {
                        if (Json.path(part, "thought") == Boolean.TRUE) continue;
                        if (Json.path(part, "text") instanceof String text && !text.isEmpty()) {
                            any = true;
                            try {
                                onText.accept(text);
                            } catch (RuntimeException exception) {
                                throw new ReceiverFailed(exception);
                            }
                        }
                    }
                }
            }
            if (!any) {
                log.warn("Gemini gave no text{}", blocked == null ? "" : " (blocked: " + blocked + ")");
                throw new TutorProviderException("The tutor had no answer for that. Try asking another way.");
            }
        } catch (ReceiverFailed failed) {
            // Not the model's fault (e.g. the browser went away): passed on as it was
            throw failed.cause;
        } catch (java.io.UncheckedIOException exception) {
            log.warn("Gemini stream broke off: {}", exception.toString());
            throw new TutorProviderException("The tutor's answer was cut off. Try again.");
        }
    }

    private static final class ReceiverFailed extends RuntimeException {
        final RuntimeException cause;

        ReceiverFailed(RuntimeException cause) {
            super(null, null, false, false);
            this.cause = cause;
        }
    }

    private TutorProviderException failure(int status, String body) {
        String detail = "";
        try {
            if (Json.path(Json.parse(body), "error", "message") instanceof String message) detail = message;
        } catch (IllegalArgumentException ignored) {
            detail = body.length() > 200 ? body.substring(0, 200) : body;
        }
        log.warn("Gemini answered {} for model {}: {}", status, model, detail);
        return new TutorProviderException(switch (status) {
            case 429 -> "The tutor has used up its free quota for now. Try again later.";
            case 400, 401, 403 -> detail.toLowerCase().contains("api key")
                    ? "The tutor's API key is not valid. The server's GEMINI_API_KEY needs fixing."
                    : "The tutor could not take that request.";
            case 404 -> "The tutor's model (" + model + ") was not found. The server's TUTOR_MODEL needs fixing.";
            default -> "The tutor is not available right now. Try again in a moment.";
        });
    }
}
