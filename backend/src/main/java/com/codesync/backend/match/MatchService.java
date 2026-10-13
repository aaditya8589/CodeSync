package com.codesync.backend.match;

import com.codesync.backend.dto.match.MatchResponse;
import com.codesync.backend.entity.Document;
import com.codesync.backend.entity.Match;
import com.codesync.backend.entity.MatchPlayer;
import com.codesync.backend.entity.Problem;
import com.codesync.backend.entity.Room;
import com.codesync.backend.exception.InvalidRunRequestException;
import com.codesync.backend.exception.MatchConflictException;
import com.codesync.backend.exception.ProblemNotFoundException;
import com.codesync.backend.exception.RoomNotFoundException;
import com.codesync.backend.repository.DocumentRepository;
import com.codesync.backend.repository.MatchPlayerRepository;
import com.codesync.backend.repository.MatchRepository;
import com.codesync.backend.repository.ProblemRepository;
import com.codesync.backend.repository.RoomRepository;
import com.codesync.backend.repository.SubmissionRepository;
import com.codesync.backend.service.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Starting, joining and ending matches. Every change takes the room's row lock first, so two
 * people pressing Start at once get one match, and file names created on join cannot clash.
 */
@Service
public class MatchService {

    private static final Logger log = LoggerFactory.getLogger(MatchService.class);

    public static final int MAX_PROBLEMS = 3;
    public static final int MIN_MINUTES = 5;
    public static final int MAX_MINUTES = 180;

    private final RoomService roomService;
    private final RoomRepository roomRepository;
    private final MatchRepository matchRepository;
    private final MatchPlayerRepository playerRepository;
    private final ProblemRepository problemRepository;
    private final DocumentRepository documentRepository;
    private final SubmissionRepository submissionRepository;

    public MatchService(
            RoomService roomService,
            RoomRepository roomRepository,
            MatchRepository matchRepository,
            MatchPlayerRepository playerRepository,
            ProblemRepository problemRepository,
            DocumentRepository documentRepository,
            SubmissionRepository submissionRepository
    ) {
        this.roomService = roomService;
        this.roomRepository = roomRepository;
        this.matchRepository = matchRepository;
        this.playerRepository = playerRepository;
        this.problemRepository = problemRepository;
        this.documentRepository = documentRepository;
        this.submissionRepository = submissionRepository;
    }

    @Transactional
    public MatchResponse start(UUID roomId, List<String> problemSlugs, Integer durationMinutes, String username) {
        roomService.checkRoomAccess(roomId, username);
        Room room = lockRoom(roomId);
        Instant now = now();

        List<String> slugs = problemSlugs == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(problemSlugs));
        if (slugs.isEmpty() || slugs.size() > MAX_PROBLEMS) {
            throw new InvalidRunRequestException("Pick between 1 and " + MAX_PROBLEMS + " different problems");
        }
        if (durationMinutes == null || durationMinutes < MIN_MINUTES || durationMinutes > MAX_MINUTES) {
            throw new InvalidRunRequestException(
                    "A match lasts between " + MIN_MINUTES + " and " + MAX_MINUTES + " minutes");
        }
        Optional<Match> latest = matchRepository.findFirstByRoomIdOrderByStartedAtDesc(roomId);
        if (latest.isPresent() && latest.get().isRunningAt(now)) {
            throw new MatchConflictException("A match is already running in this room");
        }

        List<Problem> problems = slugs.stream()
                .map(slug -> problemRepository.findBySlug(slug)
                        .orElseThrow(() -> new ProblemNotFoundException("No problem called " + slug)))
                .toList();

        Match match = matchRepository.save(new Match(
                room, username, now, now.plus(Duration.ofMinutes(durationMinutes)), problems));
        addPlayer(match, room, username, now);

        log.info("{} started a {}-minute match in room {} with {}", username, durationMinutes, roomId, slugs);
        return view(match, username, now);
    }

    /** Gives the player their own private file for each problem. Joining twice changes nothing. */
    @Transactional
    public MatchResponse join(UUID roomId, String username) {
        roomService.checkRoomAccess(roomId, username);
        Room room = lockRoom(roomId);
        Instant now = now();

        Match match = matchRepository.findFirstByRoomIdOrderByStartedAtDesc(roomId)
                .filter(found -> found.isRunningAt(now))
                .orElseThrow(() -> new MatchConflictException("No match is running in this room"));

        if (!playerRepository.existsByMatchIdAndUsername(match.getId(), username)) {
            addPlayer(match, room, username, now);
            log.info("{} joined the match in room {}", username, roomId);
        }
        return view(match, username, now);
    }

    @Transactional
    public MatchResponse end(UUID roomId, String username) {
        roomService.checkRoomAccess(roomId, username);
        lockRoom(roomId);
        Instant now = now();

        Match match = matchRepository.findFirstByRoomIdOrderByStartedAtDesc(roomId)
                .filter(found -> found.isRunningAt(now))
                .orElseThrow(() -> new MatchConflictException("No match is running in this room"));
        if (!match.getHostUsername().equals(username)) {
            throw new MatchConflictException("Only " + match.getHostUsername() + " can end the match early");
        }

        match.endAt(now);
        UUID matchId = match.getId();
        documentRepository.revealMatchFiles(matchId, now);
        log.info("{} ended the match in room {} early", username, roomId);
        return view(matchRepository.findById(matchId).orElseThrow(), username, now);
    }

    /** The current match, or the last one with its final standings; empty if the room never had one. */
    @Transactional(readOnly = true)
    public Optional<MatchResponse> current(UUID roomId, String username) {
        roomService.checkRoomAccess(roomId, username);
        Instant now = now();
        return matchRepository.findFirstByRoomIdOrderByStartedAtDesc(roomId)
                .map(match -> view(match, username, now));
    }

    private void addPlayer(Match match, Room room, String username, Instant now) {
        playerRepository.save(new MatchPlayer(match, username, now));

        List<Problem> problems = match.getProblems();
        for (int i = 0; i < problems.size(); i++) {
            Problem problem = problems.get(i);
            String letter = Match.letter(i);
            documentRepository.save(Document.forMatchPlayer(
                    room,
                    freeFileName(room.getId(), letter + "-" + fileSafe(username)),
                    starterCode(letter, problem.getTitle()),
                    username,
                    match,
                    problem
            ));
        }
    }

    // Usernames may contain characters file names may not; the owner is stored separately anyway
    static String fileSafe(String username) {
        String safe = username.replaceAll("[^A-Za-z0-9_-]+", "_");
        return safe.length() > 40 ? safe.substring(0, 40) : safe;
    }

    private String freeFileName(UUID roomId, String base) {
        String name = base + ".cpp";
        for (int n = 2; documentRepository.existsByRoomIdAndFileNameIgnoreCase(roomId, name); n++) {
            name = base + "-" + n + ".cpp";
        }
        return name;
    }

    private static String starterCode(String letter, String title) {
        return """
                // %s. %s
                // Only you can see this file until the match ends. To use Python or Java, rename it
                // to .py or .java. Submit sends it to the judge; the time counts from the match start.
                #include <bits/stdc++.h>
                using namespace std;

                int main() {
                    ios::sync_with_stdio(false);
                    cin.tie(nullptr);

                    return 0;
                }
                """.formatted(letter, title);
    }

    // Whole milliseconds: what the database stores and the browser compares, so a time reads the
    // same before and after it has been saved
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private Room lockRoom(UUID roomId) {
        return roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found"));
    }

    private MatchResponse view(Match match, String username, Instant now) {
        List<Problem> problems = match.getProblems();
        List<MatchResponse.MatchProblem> listed = new ArrayList<>();
        for (int i = 0; i < problems.size(); i++) {
            Problem problem = problems.get(i);
            listed.add(new MatchResponse.MatchProblem(
                    Match.letter(i), problem.getSlug(), problem.getTitle(), problem.getDifficulty()));
        }

        List<String> players = playerRepository.findByMatchIdOrderByJoinedAtAsc(match.getId())
                .stream().map(MatchPlayer::getUsername).toList();
        List<Scoreboard.Attempt> attempts = submissionRepository.findByMatchIdOrderByCreatedAtAsc(match.getId())
                .stream()
                .map(submission -> new Scoreboard.Attempt(submission.getUsername(),
                        submission.getProblem().getSlug(), submission.getVerdict(), submission.getCreatedAt()))
                .toList();

        return new MatchResponse(
                match.getId().toString(),
                match.getHostUsername(),
                match.getStartedAt(),
                match.getEndsAt(),
                now,
                match.isRunningAt(now),
                players.contains(username),
                listed,
                Scoreboard.compute(listed.stream().map(MatchResponse.MatchProblem::slug).toList(),
                        players, attempts, match.getStartedAt())
        );
    }
}
