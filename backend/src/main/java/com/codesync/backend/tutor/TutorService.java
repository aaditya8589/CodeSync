package com.codesync.backend.tutor;

import com.codesync.backend.dto.tutor.TutorRequest;
import com.codesync.backend.dto.tutor.TutorStatus;
import com.codesync.backend.entity.Document;
import com.codesync.backend.entity.Match;
import com.codesync.backend.entity.MatchPlayer;
import com.codesync.backend.entity.Problem;
import com.codesync.backend.entity.Room;
import com.codesync.backend.entity.Submission;
import com.codesync.backend.entity.TestCase;
import com.codesync.backend.exception.DocumentNotFoundException;
import com.codesync.backend.exception.InvalidRunRequestException;
import com.codesync.backend.exception.RoomNotFoundException;
import com.codesync.backend.exception.TutorUnavailableException;
import com.codesync.backend.execution.Language;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Hints about the problem a room works on, based on the file the student has open and their
 * last submission. Everything is checked before the model is asked: room membership, that the
 * file is theirs to see, and that no match involving the problem is under way.
 */
@Service
public class TutorService {

    private static final Logger log = LoggerFactory.getLogger(TutorService.class);

    // A lobby nobody started within a day no longer locks its problems
    static final Duration LOBBY_LOCK = Duration.ofDays(1);

    /** Everything needed to ask the model, read before the (slow) answer starts. */
    public record Prepared(String username, String problemTitle, TutorPrompts.Level level,
                           String system, List<TutorMessage> turns) {
    }

    public record Outcome(boolean codeRemoved) {
    }

    private final TutorProvider provider;
    private final TutorRateLimiter limiter;
    private final RoomService roomService;
    private final RoomRepository roomRepository;
    private final DocumentRepository documentRepository;
    private final ProblemRepository problemRepository;
    private final SubmissionRepository submissionRepository;
    private final MatchRepository matchRepository;
    private final MatchPlayerRepository matchPlayerRepository;
    private final TransactionTemplate transactions;

    public TutorService(TutorProvider provider, TutorRateLimiter limiter, RoomService roomService,
                        RoomRepository roomRepository, DocumentRepository documentRepository,
                        ProblemRepository problemRepository, SubmissionRepository submissionRepository,
                        MatchRepository matchRepository, MatchPlayerRepository matchPlayerRepository,
                        TransactionTemplate transactions) {
        this.provider = provider;
        this.limiter = limiter;
        this.roomService = roomService;
        this.roomRepository = roomRepository;
        this.documentRepository = documentRepository;
        this.problemRepository = problemRepository;
        this.submissionRepository = submissionRepository;
        this.matchRepository = matchRepository;
        this.matchPlayerRepository = matchPlayerRepository;
        this.transactions = transactions;
    }

    public TutorStatus status(String username) {
        TutorRateLimiter.Allowance left = limiter.peek(username);
        return new TutorStatus(provider.isConfigured(), provider.name(), left.leftThisHour(), left.leftToday());
    }

    /**
     * Checks the request and builds the prompt. Counts against the user's allowance, so call
     * {@link #answer} right after (it gives the hint back if the model fails).
     */
    public Prepared prepare(UUID roomId, TutorRequest request, String username) {
        if (!provider.isConfigured()) {
            throw new TutorUnavailableException("The tutor is switched off on this server.", false);
        }
        TutorPrompts.Level level = parseLevel(request.level());
        if (level == TutorPrompts.Level.ASK && (request.question() == null || request.question().isBlank())) {
            throw new InvalidRunRequestException("Type a question for the tutor");
        }
        UUID documentId = parseId(request.documentId());
        roomService.checkRoomAccess(roomId, username);
        Instant now = Instant.now();

        Prepared prepared = transactions.execute(status -> {
            Room room = roomRepository.findById(roomId).orElseThrow(() -> new RoomNotFoundException("Room not found"));
            checkNotAMatchRoom(room, now);
            Document document = documentRepository.findByIdAndRoomId(documentId, roomId)
                    .filter(doc -> doc.isVisibleTo(username, now))
                    .orElseThrow(() -> new DocumentNotFoundException("Document not found in this room"));
            Problem problem = document.getProblem() != null ? document.getProblem() : room.getProblem();
            if (problem == null) {
                throw new InvalidRunRequestException("Pick a problem for this room first; the tutor helps with it");
            }
            checkNotInAMatchWith(problem, username, now);

            List<TestCase> samples = problemRepository.findSamples(problem.getId());
            TutorPrompts.LastAttempt last = submissionRepository
                    .findFirstByProblemIdAndUsernameOrderByCreatedAtDesc(problem.getId(), username)
                    .map(submission -> lastAttempt(submission, samples.size()))
                    .orElse(null);
            TutorPrompts.Context context = new TutorPrompts.Context(
                    problem.getTitle(), problem.getStatement(), problem.getInputFormat(), problem.getOutputFormat(),
                    problem.getConstraints(), problem.getExpectedComplexity(),
                    samples.stream().map(test -> new TutorPrompts.Example(test.getInput(), test.getExpectedOutput())).toList(),
                    document.getFileName(),
                    Language.fromFileName(document.getFileName()).map(Language::displayName).orElse(null),
                    document.getContent(), last);
            return new Prepared(username, problem.getTitle(), level, TutorPrompts.system(context),
                    TutorPrompts.turns(history(request.history()), level, request.question()));
        });

        limiter.take(username);
        return prepared;
    }

    /**
     * Streams the answer through the code guard to onText.
     *
     * @throws TutorProvider.TutorProviderException when the model fails; the hint is not counted
     */
    public Outcome answer(Prepared prepared, Consumer<String> onText) {
        CodeGuard guard = new CodeGuard();
        long started = System.nanoTime();
        try {
            provider.stream(prepared.system(), prepared.turns(), piece -> {
                String safe = guard.accept(piece);
                if (!safe.isEmpty()) onText.accept(safe);
            });
        } catch (TutorProvider.TutorProviderException exception) {
            limiter.giveBack(prepared.username());
            throw exception;
        }
        String rest = guard.finish();
        if (!rest.isEmpty()) onText.accept(rest);
        log.info("Tutor {} hint for {} on {} in {} ms{}", prepared.level(), prepared.username(), prepared.problemTitle(),
                (System.nanoTime() - started) / 1_000_000, guard.removedAny() ? " (code removed)" : "");
        return new Outcome(guard.removedAny());
    }

    // During a match the tutor would be a teammate nobody else has
    private void checkNotAMatchRoom(Room room, Instant now) {
        if (!room.isMatchRoom()) return;
        Match match = matchRepository.findFirstByRoomId(room.getId()).orElse(null);
        if (match == null || match.stateAt(now) != Match.State.ENDED) {
            throw new TutorUnavailableException("The tutor is off during a match. It's back when the match ends.", true);
        }
    }

    // ...including in another room, on a problem of a match the user is playing
    private void checkNotInAMatchWith(Problem problem, String username, Instant now) {
        if (matchPlayerRepository.inUnfinishedMatchWith(username, problem.getId(), MatchPlayer.Status.DECLINED,
                now, now.minus(LOBBY_LOCK))) {
            throw new TutorUnavailableException("You're in a match with this problem, so the tutor is off for it "
                    + "until the match ends.", true);
        }
    }

    static TutorPrompts.LastAttempt lastAttempt(Submission submission, int sampleCount) {
        Integer failed = submission.getFailedTest();
        return new TutorPrompts.LastAttempt(submission.getVerdict(), submission.getPassed(), submission.getTotal(),
                failed, failed != null && failed <= sampleCount,
                submission.getGrowthSummary(), submission.getGrowthDetail());
    }

    static TutorPrompts.Level parseLevel(String level) {
        try {
            return TutorPrompts.Level.valueOf(level == null ? "" : level.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidRunRequestException("level must be NUDGE, APPROACH, STEPS, REVIEW or ASK");
        }
    }

    private static UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidRunRequestException("documentId is required");
        }
    }

    private static List<TutorMessage> history(List<TutorRequest.Turn> turns) {
        if (turns == null) return List.of();
        return turns.stream()
                .filter(turn -> turn != null && turn.text() != null)
                .map(turn -> new TutorMessage("TUTOR".equalsIgnoreCase(turn.role())
                        ? TutorMessage.Role.TUTOR : TutorMessage.Role.STUDENT, turn.text()))
                .toList();
    }
}
