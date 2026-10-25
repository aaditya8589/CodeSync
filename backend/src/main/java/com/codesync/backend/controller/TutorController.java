package com.codesync.backend.controller;

import com.codesync.backend.dto.tutor.TutorRequest;
import com.codesync.backend.dto.tutor.TutorStatus;
import com.codesync.backend.tutor.TutorEvents;
import com.codesync.backend.tutor.TutorProvider;
import com.codesync.backend.tutor.TutorService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
public class TutorController {

    private final TutorService tutorService;

    public TutorController(TutorService tutorService) {
        this.tutorService = tutorService;
    }

    @GetMapping("/api/tutor/status")
    public TutorStatus status(Authentication authentication) {
        return tutorService.status(authentication.getName());
    }

    /**
     * Streams a hint as server-sent events: {"text"} pieces, then "done" or "error". Problems
     * found before the answer starts (no access, a match, limits) are ordinary error responses.
     * Written directly on the request thread rather than through an async emitter, which keeps
     * security and error handling the same as every other request.
     */
    @PostMapping("/api/rooms/{roomId}/tutor")
    public void hint(@PathVariable UUID roomId, @RequestBody TutorRequest request,
                     Authentication authentication, HttpServletResponse response) throws IOException {
        TutorService.Prepared prepared = tutorService.prepare(roomId, request, authentication.getName());

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        // Tells nginx not to hold the stream back until it ends
        response.setHeader("X-Accel-Buffering", "no");
        OutputStream out = response.getOutputStream();

        try {
            TutorService.Outcome outcome = tutorService.answer(prepared, text -> send(out, TutorEvents.text(text)));
            send(out, TutorEvents.done(outcome.codeRemoved()));
        } catch (TutorProvider.TutorProviderException exception) {
            try {
                send(out, TutorEvents.error(exception.getMessage()));
            } catch (UncheckedIOException gone) {
                // The browser went away too
            }
        } catch (UncheckedIOException exception) {
            // The browser went away mid-answer; nothing left to tell it
        }
    }

    private static void send(OutputStream out, String event) {
        try {
            out.write(event.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
