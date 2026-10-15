package com.codesync.backend.match;

import com.codesync.backend.dto.match.MatchCreatedResponse;
import com.codesync.backend.dto.match.MatchEntryResponse;
import com.codesync.backend.dto.match.MatchResponse;
import com.codesync.backend.dto.match.MatchSummary;
import com.codesync.backend.entity.Document;
import com.codesync.backend.entity.Match;
import com.codesync.backend.entity.MatchPlayer;
import com.codesync.backend.entity.Problem;
import com.codesync.backend.entity.Room;
import com.codesync.backend.entity.RoomMember;
import com.codesync.backend.entity.User;
import com.codesync.backend.exception.InvalidRunRequestException;
import com.codesync.backend.exception.MatchConflictException;
import com.codesync.backend.exception.ProblemNotFoundException;
import com.codesync.backend.exception.RoomNotFoundException;
import com.codesync.backend.repository.DocumentRepository;
import com.codesync.backend.repository.MatchPlayerRepository;
import com.codesync.backend.repository.MatchRepository;
import com.codesync.backend.repository.ProblemRepository;
import com.codesync.backend.repository.RoomMemberRepository;
import com.codesync.backend.repository.RoomRepository;
import com.codesync.backend.repository.SubmissionRepository;
import com.codesync.backend.repository.UserRepository;
import com.codesync.backend.service.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Matches live in their own match rooms. The host creates one (a lobby), shares its link, lets
 * people in one by one, and starts the clock for everyone at once. Every change takes the match
 * room's row lock first, so two requests at the same moment (two Starts, an approval during the
 * start) are applied one after the other.
 */
@Service
public class MatchService {

    private static final Logger log = LoggerFactory.getLogger(MatchService.class);

    public static final int MAX_PROBLEMS = 3;
    public static final int MIN_MINUTES = 5;
    public static final int MAX_MINUTES = 180;

    private final RoomService roomService;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final MatchRepository matchRepository;
    private final MatchPlayerRepository playerRepository;
    private final ProblemRepository problemRepository;
    private final DocumentRepository documentRepository;
    private final SubmissionRepository submissionRepository;

    public MatchService(
            RoomService roomService,
            RoomRepository roomRepository,
            RoomMemberRepository memberRepository,
            UserRepository userRepository,
            MatchRepository matchRepository,
            MatchPlayerRepository playerRepository,
            ProblemRepository problemRepository,
            DocumentRepository documentRepository,
            SubmissionRepository submissionRepository
    ) {
        this.roomService = roomService;
        this.roomRepository = roomRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.matchRepository = matchRepository;
        this.playerRepository = playerRepository;
        this.problemRepository = problemRepository;
        this.documentRepository = documentRepository;
        this.submissionRepository = submissionRepository;
    }

    /** Creates a match room in the lobby, with its creator as host and first player. */
    @Transactional
    public MatchCreatedResponse create(List<String> problemSlugs, Integer durationMinutes, UUID fromRoomId,
                                       String username) {
        if (fromRoomId != null) {
            roomService.checkRoomAccess(fromRoomId, username);
        }
        List<String> slugs = problemSlugs == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(problemSlugs));
        if (slugs.isEmpty() || slugs.size() > MAX_PROBLEMS) {
            throw new InvalidRunRequestException("Pick between 1 and " + MAX_PROBLEMS + " different problems");
        }
        if (durationMinutes == null || durationMinutes < MIN_MINUTES || durationMinutes > MAX_MINUTES) {
            throw new InvalidRunRequestException(
                    "A match lasts between " + MIN_MINUTES + " and " + MAX_MINUTES + " minutes");
        }
        List<Problem> problems = slugs.stream()
                .map(slug -> problemRepository.findBySlug(slug)
                        .orElseThrow(() -> new ProblemNotFoundException("No problem called " + slug)))
                .toList();

        User host = user(username);
        String title = title(problems);
        Room room = roomRepository.save(Room.forMatch(title, host));
        memberRepository.save(new RoomMember(room, host, "OWNER"));

        Instant now = now();
        Match match = matchRepository.save(new Match(room, username, durationMinutes, problems, fromRoomId, now));
        playerRepository.save(new MatchPlayer(match, username, now, MatchPlayer.Status.APPROVED));

        log.info("{} created match room {} ({} min, {})", username, room.getId(), durationMinutes, slugs);
        return new MatchCreatedResponse(room.getId().toString(), title);
    }

    /** What anyone signed in sees when they open a match link, before being let in. */
    @Transactional(readOnly = true)
    public MatchEntryResponse entry(UUID roomId, String username) {
        Match match = matchOf(roomId);
        return entryView(match, username, now());
    }

    /** Asks the host to be let in. Asking again changes nothing; a declined request stays declined. */
    @Transactional
    public MatchEntryResponse requestToJoin(UUID roomId, String username) {
        user(username);
        lockRoom(roomId);
        Match match = matchOf(roomId);
        Instant now = now();
        if (match.stateAt(now) == Match.State.ENDED) {
            throw new MatchConflictException("This match is over");
        }
        if (playerRepository.findByMatchIdAndUsername(match.getId(), username).isEmpty()) {
            playerRepository.save(new MatchPlayer(match, username, now, MatchPlayer.Status.PENDING));
            log.info("{} asked to join match room {}", username, roomId);
        }
        return entryView(match, username, now);
    }

    @Transactional
    public MatchResponse approve(UUID roomId, String player, String username) {
        Match match = hostAction(roomId, username);
        Instant now = now();
        if (match.stateAt(now) == Match.State.ENDED) {
            throw new MatchConflictException("This match is over");
        }
        MatchPlayer request = playerRepository.findByMatchIdAndUsername(match.getId(), player)
                .orElseThrow(() -> new MatchConflictException(player + " has not asked to join"));
        if (request.getStatus() != MatchPlayer.Status.APPROVED) {
            request.setStatus(MatchPlayer.Status.APPROVED);
            User user = user(player);
            if (!memberRepository.existsByRoomIdAndUserId(roomId, user.getId())) {
                memberRepository.save(new RoomMember(match.getRoom(), user, "MEMBER"));
            }
            // Let in after the start: their files are made now; their time still counts from the start
            if (match.isRunningAt(now)) {
                createFiles(match, player);
            }
            log.info("{} let {} into match room {}", username, player, roomId);
        }
        return view(match, username, now);
    }

    @Transactional
    public MatchResponse decline(UUID roomId, String player, String username) {
        Match match = hostAction(roomId, username);
        MatchPlayer request = playerRepository.findByMatchIdAndUsername(match.getId(), player)
                .orElseThrow(() -> new MatchConflictException(player + " has not asked to join"));
        if (request.getStatus() == MatchPlayer.Status.APPROVED) {
            throw new MatchConflictException(player + " is already playing");
        }
        request.setStatus(MatchPlayer.Status.DECLINED);
        return view(match, username, now());
    }

    /** Starts everyone's clock at once and gives every player their private files. */
    @Transactional
    public MatchResponse start(UUID roomId, String username) {
        Match match = hostAction(roomId, username);
        Instant now = now();
        if (match.stateAt(now) != Match.State.LOBBY) {
            throw new MatchConflictException("The match has already started");
        }
        match.start(now);
        for (String player : approvedPlayers(match)) {
            createFiles(match, player);
        }
        log.info("{} started match room {}", username, roomId);
        return view(match, username, now);
    }

    @Transactional
    public MatchResponse end(UUID roomId, String username) {
        Match match = hostAction(roomId, username);
        Instant now = now();
        if (!match.isRunningAt(now)) {
            throw new MatchConflictException("The match is not running");
        }
        match.endAt(now);
        UUID matchId = match.getId();
        documentRepository.revealMatchFiles(matchId, now);
        log.info("{} ended match room {} early", username, roomId);
        return view(matchRepository.findById(matchId).orElseThrow(), username, now);
    }

    /** The match of a match room, for its members. */
    @Transactional(readOnly = true)
    public MatchResponse current(UUID roomId, String username) {
        roomService.checkRoomAccess(roomId, username);
        return view(matchOf(roomId), username, now());
    }

    /** Match rooms made from an ordinary room, so its members can find their links. */
    @Transactional(readOnly = true)
    public List<MatchSummary> madeFrom(UUID roomId, String username) {
        roomService.checkRoomAccess(roomId, username);
        Instant now = now();
        return matchRepository.findTop5ByOriginRoomIdOrderByCreatedAtDesc(roomId).stream()
                .map(match -> new MatchSummary(match.getRoom().getId().toString(), match.getRoom().getName(),
                        match.getHostUsername(), match.stateAt(now).name()))
                .toList();
    }

    private Match hostAction(UUID roomId, String username) {
        roomService.checkRoomAccess(roomId, username);
        lockRoom(roomId);
        Match match = matchOf(roomId);
        if (!match.getHostUsername().equals(username)) {
            throw new MatchConflictException("Only the host, " + match.getHostUsername() + ", can do that");
        }
        return match;
    }

    private Match matchOf(UUID roomId) {
        return matchRepository.findFirstByRoomId(roomId)
                .orElseThrow(() -> new RoomNotFoundException("No match here"));
    }

    private List<String> approvedPlayers(Match match) {
        return playerRepository.findByMatchIdOrderByJoinedAtAsc(match.getId()).stream()
                .filter(player -> player.getStatus() == MatchPlayer.Status.APPROVED)
                .map(MatchPlayer::getUsername)
                .toList();
    }

    private void createFiles(Match match, String username) {
        List<Problem> problems = match.getProblems();
        for (int i = 0; i < problems.size(); i++) {
            Problem problem = problems.get(i);
            String letter = Match.letter(i);
            documentRepository.save(Document.forMatchPlayer(
                    match.getRoom(),
                    freeFileName(match.getRoom().getId(), letter + "-" + fileSafe(username)),
                    starterCode(letter, problem.getTitle()),
                    username,
                    match,
                    problem
            ));
        }
    }

    private static String title(List<Problem> problems) {
        String title = "Match: " + String.join(", ", problems.stream().map(Problem::getTitle).toList());
        return title.length() > 100 ? title.substring(0, 97) + "..." : title;
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

    private User user(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private Room lockRoom(UUID roomId) {
        return roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found"));
    }

    private MatchEntryResponse entryView(Match match, String username, Instant now) {
        String status = playerRepository.findByMatchIdAndUsername(match.getId(), username)
                .map(player -> player.getStatus().name())
                .orElse("NONE");
        return new MatchEntryResponse(match.getRoom().getId().toString(), match.getRoom().getName(),
                match.getHostUsername(), match.stateAt(now).name(), match.getProblems().size(),
                match.getDurationMinutes(), status);
    }

    private MatchResponse view(Match match, String username, Instant now) {
        List<Problem> problems = match.getProblems();
        List<MatchResponse.MatchProblem> listed = new ArrayList<>();
        for (int i = 0; i < problems.size(); i++) {
            Problem problem = problems.get(i);
            listed.add(new MatchResponse.MatchProblem(
                    Match.letter(i), problem.getSlug(), problem.getTitle(), problem.getDifficulty()));
        }

        List<MatchPlayer> everyone = playerRepository.findByMatchIdOrderByJoinedAtAsc(match.getId());
        List<String> players = everyone.stream()
                .filter(player -> player.getStatus() == MatchPlayer.Status.APPROVED)
                .map(MatchPlayer::getUsername).toList();
        // Who is waiting is the host's business
        List<String> requests = !match.getHostUsername().equals(username) ? List.of() : everyone.stream()
                .filter(player -> player.getStatus() == MatchPlayer.Status.PENDING)
                .map(MatchPlayer::getUsername).toList();
        List<Scoreboard.Attempt> attempts = submissionRepository.findByMatchIdOrderByCreatedAtAsc(match.getId())
                .stream()
                .map(submission -> new Scoreboard.Attempt(submission.getUsername(),
                        submission.getProblem().getSlug(), submission.getVerdict(), submission.getCreatedAt()))
                .toList();

        Match.State state = match.stateAt(now);
        return new MatchResponse(
                match.getId().toString(),
                match.getHostUsername(),
                state.name(),
                match.getDurationMinutes(),
                match.getStartedAt(),
                match.getEndsAt(),
                now,
                state == Match.State.RUNNING,
                players.contains(username),
                listed,
                Scoreboard.compute(listed.stream().map(MatchResponse.MatchProblem::slug).toList(), players, attempts,
                        match.getStartedAt() == null ? now : match.getStartedAt()),
                requests
        );
    }
}
