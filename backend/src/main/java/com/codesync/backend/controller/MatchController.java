package com.codesync.backend.controller;

import com.codesync.backend.dto.match.MatchChangedMessage;
import com.codesync.backend.dto.match.MatchResponse;
import com.codesync.backend.dto.match.StartMatchRequest;
import com.codesync.backend.match.MatchService;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/rooms/{roomId}/match")
public class MatchController {

    private final MatchService matchService;
    private final SimpMessagingTemplate messagingTemplate;

    public MatchController(MatchService matchService, SimpMessagingTemplate messagingTemplate) {
        this.matchService = matchService;
        this.messagingTemplate = messagingTemplate;
    }

    /** 204 when the room has never had a match. */
    @GetMapping
    public ResponseEntity<MatchResponse> current(@PathVariable UUID roomId, Authentication authentication) {
        return matchService.current(roomId, authentication.getName())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping
    public MatchResponse start(@PathVariable UUID roomId, @RequestBody StartMatchRequest request,
                               Authentication authentication) {
        MatchResponse match = matchService.start(
                roomId, request.problemSlugs(), request.durationMinutes(), authentication.getName());
        announce(roomId, "STARTED", authentication.getName());
        return match;
    }

    @PostMapping("/join")
    public MatchResponse join(@PathVariable UUID roomId, Authentication authentication) {
        MatchResponse match = matchService.join(roomId, authentication.getName());
        announce(roomId, "JOINED", authentication.getName());
        return match;
    }

    @PostMapping("/end")
    public MatchResponse end(@PathVariable UUID roomId, Authentication authentication) {
        MatchResponse match = matchService.end(roomId, authentication.getName());
        announce(roomId, "ENDED", authentication.getName());
        return match;
    }

    // Only that something changed; each tab fetches what it is allowed to see
    private void announce(UUID roomId, String event, String username) {
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/match", new MatchChangedMessage(event, username));
    }
}
