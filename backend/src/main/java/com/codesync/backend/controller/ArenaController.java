package com.codesync.backend.controller;

import com.codesync.backend.arena.ArenaService;
import com.codesync.backend.arena.Judge;
import com.codesync.backend.dto.arena.AttachProblemRequest;
import com.codesync.backend.dto.arena.ProblemDetail;
import com.codesync.backend.dto.arena.ProblemSummary;
import com.codesync.backend.dto.arena.RoomProblemMessage;
import com.codesync.backend.dto.arena.SubmissionMessage;
import com.codesync.backend.dto.arena.SubmissionSummary;
import com.codesync.backend.dto.arena.SubmitRequest;
import com.codesync.backend.exception.InvalidRunRequestException;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class ArenaController {

    private final ArenaService arenaService;
    private final SimpMessagingTemplate messagingTemplate;

    public ArenaController(ArenaService arenaService, SimpMessagingTemplate messagingTemplate) {
        this.arenaService = arenaService;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping("/api/problems")
    public List<ProblemSummary> problems(Authentication authentication) {
        return arenaService.listProblems(authentication.getName());
    }

    @GetMapping("/api/problems/{slug}")
    public ProblemDetail problem(@PathVariable String slug, Authentication authentication) {
        return arenaService.getProblem(slug, authentication.getName());
    }

    @GetMapping("/api/problems/{slug}/submissions")
    public List<SubmissionSummary> mySubmissions(@PathVariable String slug, Authentication authentication) {
        return arenaService.mySubmissions(slug, authentication.getName());
    }

    @PutMapping("/api/rooms/{roomId}/problem")
    public Map<String, String> attachProblem(
            @PathVariable UUID roomId,
            @RequestBody AttachProblemRequest request,
            Authentication authentication
    ) {
        String slug = arenaService.attachProblem(roomId, request.slug(), authentication.getName());
        messagingTemplate.convertAndSend(
                "/topic/rooms/" + roomId + "/problem",
                new RoomProblemMessage(slug, authentication.getName())
        );
        return java.util.Collections.singletonMap("slug", slug);
    }

    @PostMapping("/api/rooms/{roomId}/submit")
    public ResponseEntity<Judge.Result> submit(
            @PathVariable UUID roomId,
            @RequestBody SubmitRequest request,
            Authentication authentication
    ) {
        UUID documentId = parseDocumentId(request);
        ArenaService.Judged judged = arenaService.submit(roomId, documentId, authentication.getName());
        Judge.Result result = judged.result();

        messagingTemplate.convertAndSend(
                "/topic/rooms/" + roomId + "/submissions",
                new SubmissionMessage(authentication.getName(), judged.problemTitle(), judged.fileName(),
                        result.verdict().name(), result.passed(), result.total(), result.failedTest())
        );
        return ResponseEntity.ok(result);
    }

    private static UUID parseDocumentId(SubmitRequest request) {
        try {
            return UUID.fromString(request.documentId());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidRunRequestException("documentId is required");
        }
    }
}
