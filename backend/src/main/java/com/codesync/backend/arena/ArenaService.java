package com.codesync.backend.arena;

import com.codesync.backend.dto.arena.ProblemDetail;
import com.codesync.backend.dto.arena.ProblemSummary;
import com.codesync.backend.dto.arena.ProgressResponse;
import com.codesync.backend.dto.arena.SubmissionSummary;
import com.codesync.backend.entity.Document;
import com.codesync.backend.entity.Match;
import com.codesync.backend.entity.Problem;
import com.codesync.backend.entity.Room;
import com.codesync.backend.entity.Submission;
import com.codesync.backend.exception.DocumentNotFoundException;
import com.codesync.backend.exception.InvalidRunRequestException;
import com.codesync.backend.exception.ProblemNotFoundException;
import com.codesync.backend.exception.RoomNotFoundException;
import com.codesync.backend.exception.UnsupportedLanguageException;
import com.codesync.backend.execution.CodeRunner;
import com.codesync.backend.execution.ExecutionService;
import com.codesync.backend.execution.Language;
import com.codesync.backend.repository.DocumentRepository;
import com.codesync.backend.repository.ProblemRepository;
import com.codesync.backend.repository.RoomRepository;
import com.codesync.backend.repository.SubmissionRepository;
import com.codesync.backend.service.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ArenaService {

    private static final Logger log = LoggerFactory.getLogger(ArenaService.class);

    /** What a submission needs from the database, read before the (slow) judging starts. */
    private record Attempt(UUID problemId, String problemTitle, List<ProblemFile.Test> tests,
                           String fileName, String source, UUID matchId, String expectedComplexity) {
    }

    /** matchId is set when the submission counts toward a match's standings. */
    public record Judged(String problemTitle, String fileName, Judge.Result result, UUID matchId) {
    }

    private final ProblemRepository problemRepository;
    private final SubmissionRepository submissionRepository;
    private final RoomRepository roomRepository;
    private final DocumentRepository documentRepository;
    private final RoomService roomService;
    private final ExecutionService executionService;
    private final TransactionTemplate transactions;

    public ArenaService(
            ProblemRepository problemRepository,
            SubmissionRepository submissionRepository,
            RoomRepository roomRepository,
            DocumentRepository documentRepository,
            RoomService roomService,
            ExecutionService executionService,
            TransactionTemplate transactions
    ) {
        this.problemRepository = problemRepository;
        this.submissionRepository = submissionRepository;
        this.roomRepository = roomRepository;
        this.documentRepository = documentRepository;
        this.roomService = roomService;
        this.executionService = executionService;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public List<ProblemSummary> listProblems(String username) {
        Set<String> solved = new HashSet<>(submissionRepository.findSolvedSlugs(username));
        return problemRepository.findAllByOrderByPositionAsc().stream()
                .map(problem -> new ProblemSummary(problem.getSlug(), problem.getTitle(),
                        problem.getDifficulty(), solved.contains(problem.getSlug())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProgressResponse progress(String username) {
        record Tried(long attempts, long accepted, Instant firstAccepted) {
        }
        Map<String, Tried> tried = new HashMap<>();
        for (Object[] row : submissionRepository.summarizeByProblem(username)) {
            tried.put((String) row[0], new Tried(((Number) row[1]).longValue(),
                    row[2] == null ? 0 : ((Number) row[2]).longValue(), (Instant) row[3]));
        }

        List<ProgressResponse.ProblemProgress> problems = new ArrayList<>();
        Map<String, int[]> levels = new LinkedHashMap<>();
        for (String difficulty : List.of("Easy", "Medium", "Hard")) levels.put(difficulty, new int[3]);
        for (Problem problem : problemRepository.findAllByOrderByPositionAsc()) {
            Tried attempt = tried.get(problem.getSlug());
            String status = attempt == null ? "NEW" : attempt.accepted() > 0 ? "SOLVED" : "ATTEMPTED";
            problems.add(new ProgressResponse.ProblemProgress(problem.getSlug(), problem.getTitle(),
                    problem.getDifficulty(), status, attempt == null ? 0 : (int) attempt.attempts(),
                    attempt == null ? null : attempt.firstAccepted()));
            int[] counts = levels.computeIfAbsent(problem.getDifficulty(), key -> new int[3]);
            if (status.equals("SOLVED")) counts[0]++;
            if (status.equals("ATTEMPTED")) counts[1]++;
            counts[2]++;
        }
        List<ProgressResponse.Level> summary = levels.entrySet().stream()
                .filter(entry -> entry.getValue()[2] > 0)
                .map(entry -> new ProgressResponse.Level(entry.getKey(), entry.getValue()[0],
                        entry.getValue()[1], entry.getValue()[2]))
                .toList();
        return new ProgressResponse(summary, problems);
    }

    @Transactional(readOnly = true)
    public ProblemDetail getProblem(String slug, String username) {
        Problem problem = findProblem(slug);
        List<ProblemDetail.Example> examples = problemRepository.findSamples(problem.getId()).stream()
                .map(test -> new ProblemDetail.Example(test.getInput(), test.getExpectedOutput()))
                .toList();
        boolean solved = submissionRepository.existsByProblemIdAndUsernameAndVerdict(
                problem.getId(), username, Verdict.ACCEPTED.name());

        return new ProblemDetail(problem.getSlug(), problem.getTitle(), problem.getDifficulty(),
                problem.getStatement(), problem.getInputFormat(), problem.getOutputFormat(),
                problem.getConstraints(), problem.getExpectedComplexity(), examples,
                (int) problemRepository.countTests(problem.getId()), solved);
    }

    @Transactional(readOnly = true)
    public List<SubmissionSummary> mySubmissions(String slug, String username) {
        Problem problem = findProblem(slug);
        return submissionRepository.findTop20ByProblemIdAndUsernameOrderByCreatedAtDesc(problem.getId(), username)
                .stream()
                .map(submission -> new SubmissionSummary(
                        submission.getId().toString(),
                        submission.getVerdict(),
                        submission.getPassed(),
                        submission.getTotal(),
                        submission.getTimeMs(),
                        submission.getLanguage(),
                        submission.getFileName(),
                        submission.getRoomId() == null ? null : submission.getRoomId().toString(),
                        submission.getCreatedAt()))
                .toList();
    }

    /** Sets (or with a null slug, removes) the problem the room works on. Returns the new slug. */
    @Transactional
    public String attachProblem(UUID roomId, String slug, String username) {
        roomService.checkRoomAccess(roomId, username);
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new RoomNotFoundException("Room not found"));
        room.setProblem(slug == null || slug.isBlank() ? null : findProblem(slug));
        return room.getProblem() == null ? null : room.getProblem().getSlug();
    }

    /**
     * Judges the saved copy of a file against the room's problem, like Run does, so everyone
     * in the room submits exactly what they all see. Not one transaction: judging takes seconds
     * and must not hold a database connection meanwhile.
     */
    public Judged submit(UUID roomId, UUID documentId, String username) {
        roomService.checkRoomAccess(roomId, username);
        // The match clock is read when the request arrives; judging may finish after the end
        Instant submittedAt = Instant.now();

        Attempt attempt = transactions.execute(status -> {
            Room room = roomRepository.findById(roomId).orElseThrow(() -> new RoomNotFoundException("Room not found"));
            Document document = documentRepository.findByIdAndRoomId(documentId, roomId)
                    .filter(doc -> doc.isVisibleTo(username, submittedAt))
                    .orElseThrow(() -> new DocumentNotFoundException("Document not found in this room"));

            // A match file is judged against its own problem, and counts while its match runs;
            // anything else is practice on the room's problem
            Problem problem = document.getProblem() != null ? document.getProblem() : room.getProblem();
            if (problem == null) {
                throw new InvalidRunRequestException("Pick a problem for this room before submitting");
            }
            Match match = document.getMatch();
            UUID matchId = match != null && match.isRunningAt(submittedAt)
                    && username.equals(document.getOwnerUsername()) ? match.getId() : null;

            List<ProblemFile.Test> tests = problem.getTestCases().stream()
                    .map(test -> new ProblemFile.Test(test.isSample(), test.getInput(), test.getExpectedOutput()))
                    .toList();
            return new Attempt(problem.getId(), problem.getTitle(), tests, document.getFileName(),
                    document.getContent(), matchId, problem.getExpectedComplexity());
        });

        Language language = Language.fromFileName(attempt.fileName())
                .orElseThrow(() -> new UnsupportedLanguageException(
                        "Only C++ (.cpp), Python (.py) and Java (.java) files can be submitted"));

        CodeRunner.TestRun run = executionService.runTests(
                language, attempt.source(), attempt.tests().stream().map(ProblemFile.Test::input).toList());
        Judge.Result result = Judge.judge(attempt.tests(), run, attempt.expectedComplexity());

        Submission submission = new Submission(
                problemRepository.getReferenceById(attempt.problemId()),
                username,
                roomId,
                attempt.fileName(),
                language.name(),
                attempt.source(),
                result.verdict().name(),
                result.passed(),
                result.total(),
                result.maxTimeMs(),
                attempt.matchId(),
                submittedAt
        );
        submission.recordDetails(result.failedTest(),
                result.growth() == null ? null : result.growth().summary(),
                result.growth() == null ? null : result.growth().detail());
        transactions.executeWithoutResult(status -> submissionRepository.save(submission));

        log.info("Submission by {} for {}: {} ({}/{})", username, attempt.problemTitle(),
                result.verdict(), result.passed(), result.total());
        return new Judged(attempt.problemTitle(), attempt.fileName(), result, attempt.matchId());
    }

    private Problem findProblem(String slug) {
        return problemRepository.findBySlug(slug)
                .orElseThrow(() -> new ProblemNotFoundException("No problem called " + slug));
    }
}
