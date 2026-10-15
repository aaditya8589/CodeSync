package com.codesync.backend.controller;

import com.codesync.backend.dto.match.CreateMatchRequest;
import com.codesync.backend.dto.match.MatchChangedMessage;
import com.codesync.backend.dto.match.MatchCreatedResponse;
import com.codesync.backend.dto.match.MatchEntryResponse;
import com.codesync.backend.dto.match.MatchResponse;
import com.codesync.backend.dto.match.MatchSummary;
import com.codesync.backend.exception.InvalidRunRequestException;
import com.codesync.backend.match.MatchService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * /api/matches/...: creating a match room and getting in through its link (any signed-in user).
 * /api/rooms/{id}/match/...: the match inside its room (members; the host runs it).
 */
@RestController
public class MatchController {

    private final MatchService matchService;
    private final SimpMessagingTemplate messagingTemplate;

    public MatchController(MatchService matchService, SimpMessagingTemplate messagingTemplate) {
        this.matchService = matchService;
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping("/api/matches")
    public MatchCreatedResponse create(@RequestBody CreateMatchRequest request, Authentication authentication) {
        UUID fromRoom = parseRoomId(request.fromRoomId());
        MatchCreatedResponse created = matchService.create(
                request.problemSlugs(), request.durationMinutes(), fromRoom, authentication.getName());
        if (fromRoom != null) {
            messagingTemplate.convertAndSend("/topic/rooms/" + fromRoom + "/match",
                    new MatchChangedMessage("CREATED", authentication.getName(), created.roomId()));
        }
        return created;
    }

    @GetMapping("/api/matches/{roomId}")
    public MatchEntryResponse entry(@PathVariable UUID roomId, Authentication authentication) {
        return matchService.entry(roomId, authentication.getName());
    }

    @PostMapping("/api/matches/{roomId}/requests")
    public MatchEntryResponse requestToJoin(@PathVariable UUID roomId, Authentication authentication) {
        MatchEntryResponse entry = matchService.requestToJoin(roomId, authentication.getName());
        if ("PENDING".equals(entry.myStatus())) {
            announce(roomId, "REQUESTED", authentication.getName());
        }
        return entry;
    }

    @GetMapping("/api/rooms/{roomId}/match")
    public MatchResponse current(@PathVariable UUID roomId, Authentication authentication) {
        return matchService.current(roomId, authentication.getName());
    }

    @GetMapping("/api/rooms/{roomId}/matches")
    public List<MatchSummary> madeFrom(@PathVariable UUID roomId, Authentication authentication) {
        return matchService.madeFrom(roomId, authentication.getName());
    }

    @PostMapping("/api/rooms/{roomId}/match/requests/{username}/approve")
    public MatchResponse approve(@PathVariable UUID roomId, @PathVariable String username,
                                 Authentication authentication) {
        MatchResponse match = matchService.approve(roomId, username, authentication.getName());
        announce(roomId, "APPROVED", username);
        return match;
    }

    @PostMapping("/api/rooms/{roomId}/match/requests/{username}/decline")
    public MatchResponse decline(@PathVariable UUID roomId, @PathVariable String username,
                                 Authentication authentication) {
        return matchService.decline(roomId, username, authentication.getName());
    }

    @PostMapping("/api/rooms/{roomId}/match/start")
    public MatchResponse start(@PathVariable UUID roomId, Authentication authentication) {
        MatchResponse match = matchService.start(roomId, authentication.getName());
        announce(roomId, "STARTED", authentication.getName());
        return match;
    }

    @PostMapping("/api/rooms/{roomId}/match/end")
    public MatchResponse end(@PathVariable UUID roomId, Authentication authentication) {
        MatchResponse match = matchService.end(roomId, authentication.getName());
        announce(roomId, "ENDED", authentication.getName());
        return match;
    }

    // Only that something changed; each tab fetches what it is allowed to see
    private void announce(UUID roomId, String event, String username) {
        messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/match", new MatchChangedMessage(event, username));
    }

    private static UUID parseRoomId(String roomId) {
        if (roomId == null || roomId.isBlank()) return null;
        try {
            return UUID.fromString(roomId);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRunRequestException("fromRoomId is not a room id");
        }
    }
}
