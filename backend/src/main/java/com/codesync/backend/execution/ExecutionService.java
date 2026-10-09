package com.codesync.backend.execution;

import com.codesync.backend.entity.Document;
import com.codesync.backend.exception.DocumentNotFoundException;
import com.codesync.backend.exception.ExecutionBusyException;
import com.codesync.backend.exception.UnsupportedLanguageException;
import com.codesync.backend.repository.DocumentRepository;
import com.codesync.backend.service.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;

@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private final DocumentRepository documentRepository;
    private final RoomService roomService;
    private final CodeRunner codeRunner;
    // Each run uses a CPU and 256 MB; this caps how many the machine runs at once
    private final Semaphore slots;

    public ExecutionService(
            DocumentRepository documentRepository,
            RoomService roomService,
            @Value("${codesync.execution.docker-command:docker}") String dockerCommand,
            @Value("${codesync.execution.image:gcc:14}") String image,
            @Value("${codesync.execution.max-concurrent:2}") int maxConcurrent
    ) {
        this.documentRepository = documentRepository;
        this.roomService = roomService;
        this.codeRunner = new CodeRunner(List.of(dockerCommand), image, 2, Duration.ofSeconds(20), 64 * 1024);
        this.slots = new Semaphore(maxConcurrent);
    }

    // Deliberately not @Transactional: a run can take seconds, and it must not hold a
    // database connection while it waits for Docker.
    public ExecutionResult run(UUID roomId, UUID documentId, String username) {
        roomService.checkRoomAccess(roomId, username);

        // Runs the saved, server-side copy: everyone in the room runs the same code
        Document document = documentRepository
                .findByIdAndRoomId(documentId, roomId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found in this room"));

        if (!document.getFileName().endsWith(".cpp")) {
            throw new UnsupportedLanguageException("Only C++ (.cpp) files can be run so far");
        }

        if (!slots.tryAcquire()) {
            throw new ExecutionBusyException("The code runner is busy. Try again in a few seconds.");
        }

        try {
            ExecutionResult result = codeRunner.runCpp(document.getContent());
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
