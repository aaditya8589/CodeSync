package com.codesync.backend.arena;

import com.codesync.backend.entity.Problem;
import com.codesync.backend.entity.TestCase;
import com.codesync.backend.repository.ProblemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The problem files in resources/problems are the source of truth. At startup they are copied
 * into the database (added or updated by slug), so adding a problem is adding a file.
 */
@Component
public class ProblemCatalog {

    private static final Logger log = LoggerFactory.getLogger(ProblemCatalog.class);

    private final ProblemRepository problemRepository;

    public ProblemCatalog(ProblemRepository problemRepository) {
        this.problemRepository = problemRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void syncProblems() {
        List<ProblemFile> files = loadAll();
        int changed = 0;
        for (ProblemFile file : files) {
            Problem problem = problemRepository.findBySlug(file.slug()).orElseGet(() -> new Problem(file.slug()));
            // The files hold megabytes of tests; rewriting them all on every start would be slow
            if (file.contentHash().equals(problem.getContentHash())) {
                continue;
            }
            changed++;
            problem.update(file.title(), file.difficulty(), file.position(), file.statement(),
                    file.inputFormat(), file.outputFormat(), file.constraints(),
                    file.expectedComplexity(), file.contentHash());
            problem.setInterviewMaterial(file.interviewerGuide(), file.hints());

            problem.getTestCases().clear();
            for (int i = 0; i < file.tests().size(); i++) {
                ProblemFile.Test test = file.tests().get(i);
                problem.getTestCases().add(new TestCase(problem, i, test.sample(), test.input(), test.expectedOutput()));
            }
            problemRepository.save(problem);
        }
        log.info("Loaded {} problems ({} new or changed)", files.size(), changed);
    }

    public static List<ProblemFile> loadAll() {
        List<ProblemFile> problems = new ArrayList<>();
        for (String slug : read("problems/index.txt").split("\n")) {
            if (!slug.isBlank()) {
                problems.add(ProblemFile.parse(slug.strip(), read("problems/" + slug.strip() + ".txt")));
            }
        }
        return problems;
    }

    private static String read(String path) {
        try (InputStream stream = new ClassPathResource(path).getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot read " + path, exception);
        }
    }
}
