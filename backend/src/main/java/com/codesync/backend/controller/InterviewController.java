package com.codesync.backend.controller;

import com.codesync.backend.dto.interview.CreateInterviewRequest;
import com.codesync.backend.dto.interview.InterviewChangedMessage;
import com.codesync.backend.dto.interview.InterviewCreatedResponse;
import com.codesync.backend.dto.interview.InterviewEntryResponse;
import com.codesync.backend.dto.interview.InterviewResponse;
import com.codesync.backend.dto.interview.InterviewSummary;
import com.codesync.backend.dto.interview.NoteRequest;
import com.codesync.backend.dto.interview.ScorecardRequest;
import com.codesync.backend.interview.InterviewService;
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
 * /api/interviews/...: creating a mock interview and getting in through its link.
 * /api/rooms/{id}/interview/...: the interview inside its room (the interviewer runs it).
 * Changes are announced on /topic/rooms/{id}/interview without details: each side then fetches
 * its own view, so interviewer-only notes and hints never travel to the candidate.
 */
@RestController
public class InterviewController {

    private final InterviewService interviewService;
    private final SimpMessagingTemplate messagingTemplate;

    public InterviewController(InterviewService interviewService, SimpMessagingTemplate messagingTemplate) {
        this.interviewService = interviewService;
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping("/api/interviews")
    public InterviewCreatedResponse create(@RequestBody CreateInterviewRequest request, Authentication authentication) {
        return interviewService.create(request.problemSlug(), request.difficulty(), request.durationMinutes(),
                request.meetingLink(), authentication.getName());
    }

    @GetMapping("/api/interviews/mine")
    public List<InterviewSummary> mine(Authentication authentication) {
        return interviewService.mine(authentication.getName());
    }

    @GetMapping("/api/interviews/{roomId}")
    public InterviewEntryResponse entry(@PathVariable UUID roomId, Authentication authentication) {
        return interviewService.entry(roomId, authentication.getName());
    }

    @PostMapping("/api/interviews/{roomId}/requests")
    public InterviewEntryResponse requestToJoin(@PathVariable UUID roomId, Authentication authentication) {
        InterviewEntryResponse entry = interviewService.requestToJoin(roomId, authentication.getName());
        if ("PENDING".equals(entry.myStatus())) announce(roomId, "REQUESTED", authentication);
        return entry;
    }

    @GetMapping("/api/rooms/{roomId}/interview")
    public InterviewResponse current(@PathVariable UUID roomId, Authentication authentication) {
        return interviewService.current(roomId, authentication.getName());
    }

    @PostMapping("/api/rooms/{roomId}/interview/candidate/{username}/approve")
    public InterviewResponse approve(@PathVariable UUID roomId, @PathVariable String username,
                                     Authentication authentication) {
        InterviewResponse interview = interviewService.approve(roomId, username, authentication.getName());
        messagingTemplate.convertAndSend(topic(roomId), new InterviewChangedMessage("APPROVED", username));
        return interview;
    }

    @PostMapping("/api/rooms/{roomId}/interview/candidate/{username}/decline")
    public InterviewResponse decline(@PathVariable UUID roomId, @PathVariable String username,
                                     Authentication authentication) {
        return interviewService.decline(roomId, username, authentication.getName());
    }

    @PostMapping("/api/rooms/{roomId}/interview/start")
    public InterviewResponse start(@PathVariable UUID roomId, Authentication authentication) {
        return announced(roomId, "STARTED", authentication, interviewService.start(roomId, authentication.getName()));
    }

    @PostMapping("/api/rooms/{roomId}/interview/reveal")
    public InterviewResponse reveal(@PathVariable UUID roomId, Authentication authentication) {
        return announced(roomId, "REVEALED", authentication, interviewService.reveal(roomId, authentication.getName()));
    }

    @PostMapping("/api/rooms/{roomId}/interview/hints")
    public InterviewResponse giveHint(@PathVariable UUID roomId, Authentication authentication) {
        return announced(roomId, "HINT", authentication, interviewService.giveHint(roomId, authentication.getName()));
    }

    // Private: nothing is announced
    @PostMapping("/api/rooms/{roomId}/interview/notes")
    public InterviewResponse addNote(@PathVariable UUID roomId, @RequestBody NoteRequest request,
                                     Authentication authentication) {
        return interviewService.addNote(roomId, request.text(), authentication.getName());
    }

    @PostMapping("/api/rooms/{roomId}/interview/end")
    public InterviewResponse end(@PathVariable UUID roomId, Authentication authentication) {
        return announced(roomId, "ENDED", authentication, interviewService.end(roomId, authentication.getName()));
    }

    @PostMapping("/api/rooms/{roomId}/interview/scorecard")
    public InterviewResponse score(@PathVariable UUID roomId, @RequestBody ScorecardRequest request,
                                   Authentication authentication) {
        return announced(roomId, "SCORED", authentication, interviewService.score(roomId, request, authentication.getName()));
    }

    @PostMapping("/api/rooms/{roomId}/interview/swap")
    public InterviewCreatedResponse swap(@PathVariable UUID roomId, Authentication authentication) {
        InterviewCreatedResponse next = interviewService.swap(roomId, authentication.getName());
        messagingTemplate.convertAndSend(topic(roomId),
                new InterviewChangedMessage("SWAPPED", authentication.getName(), next.roomId()));
        return next;
    }

    private InterviewResponse announced(UUID roomId, String event, Authentication authentication,
                                        InterviewResponse interview) {
        announce(roomId, event, authentication);
        return interview;
    }

    private void announce(UUID roomId, String event, Authentication authentication) {
        messagingTemplate.convertAndSend(topic(roomId), new InterviewChangedMessage(event, authentication.getName()));
    }

    private static String topic(UUID roomId) {
        return "/topic/rooms/" + roomId + "/interview";
    }
}
