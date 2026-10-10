package com.codesync.backend.execution;

import com.codesync.backend.entity.Document;
import com.codesync.backend.exception.DocumentNotFoundException;
import com.codesync.backend.exception.ExecutionBusyException;
import com.codesync.backend.exception.InvalidRunRequestException;
import com.codesync.backend.exception.UnsupportedLanguageException;
import com.codesync.backend.repository.DocumentRepository;
import com.codesync.backend.service.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;

@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    public static final int MAX_INPUT_BYTES = 64 * 1024;

    private final DocumentRepository documentRepository;
    private final RoomService roomService;
    private final CodeRunner codeRunner;
    // Each run uses a CPU and 256 MB; this caps how many the machine runs at once
    private final Semaphore slots;

    public ExecutionService(
            DocumentRepository documentRepository,
            RoomService roomService,
            @Value("${codesync.execution.docker-command:docker}") String dockerCommand,
            @Value("${codesync.execution.image.cpp}") String cppImage,
            @Value("${codesync.execution.image.python}") String pythonImage,
            @Value("${codesync.execution.image.java}") String javaImage,
            @Value("${codesync.execution.max-concurrent:2}") int maxConcurrent
    ) {
        this.documentRepository = documentRepository;
        this.roomService = roomService;
        this.codeRunner = new CodeRunner(
                List.of(dockerCommand),
                Map.of(Language.CPP, cppImage, Language.PYTHON, pythonImage, Language.JAVA, javaImage),
                Duration.ofSeconds(20),
                64 * 1024
        );
        this.slots = new Semaphore(maxConcurrent);
    }

    // Deliberately not @Transactional: a run can take seconds, and it must not hold a
    // database connection while it waits for Docker.
    public ExecutionResult run(UUID roomId, UUID documentId, String stdin, String username) {
        roomService.checkRoomAccess(roomId, username);

        String input = stdin == null ? "" : stdin;
        if (input.getBytes(StandardCharsets.UTF_8).length > MAX_INPUT_BYTES) {
            throw new InvalidRunRequestException("Input is larger than " + (MAX_INPUT_BYTES / 1024) + " KB");
        }

        // Runs the saved, server-side copy: everyone in the room runs the same code
        Document document = documentRepository
                .findByIdAndRoomId(documentId, roomId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found in this room"));

        Language language = Language.fromFileName(document.getFileName())
                .orElseThrow(() -> new UnsupportedLanguageException(
                        "Only C++ (.cpp), Python (.py) and Java (.java) files can be run"));

        if (!slots.tryAcquire()) {
            throw new ExecutionBusyException("The code runner is busy. Try again in a few seconds.");
        }

        try {
            ExecutionResult result = codeRunner.run(language, document.getContent(), input);
            log.info("Run by {} of {}: {} in {} ms",
                    username, document.getFileName(), result.status(), result.durationMs());
            if (result.status() == ExecutionStatus.INTERNAL_ERROR) {
                log.warn("Code runner failure: {}", result.stderr());
            }
            return result;
        } finally {
            slots.release();
        }
    }
}
