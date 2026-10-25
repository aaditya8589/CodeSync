package com.codesync.backend.entity;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A judge problem, loaded from resources/problems at startup (see ProblemCatalog). */
@Entity
@Table(name = "problems")
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 20)
    private String difficulty;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String statement;

    @Column(name = "input_format", nullable = false, columnDefinition = "TEXT")
    private String inputFormat;

    @Column(name = "output_format", nullable = false, columnDefinition = "TEXT")
    private String outputFormat;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String constraints;

    @Column(name = "expected_complexity", length = 40)
    private String expectedComplexity;

    // SHA-256 of the problem file it was loaded from
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    // Mock interviews: for the interviewer only, never in the problem's public view
    @Column(name = "interviewer_guide", columnDefinition = "TEXT")
    private String interviewerGuide;

    // Hints in the order they are given, separated by HINT_SEPARATOR
    @Column(columnDefinition = "TEXT")
    private String hints;

    private static final String HINT_SEPARATOR = "\n\u001E\n";

    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<TestCase> testCases = new ArrayList<>();

    public Problem() {
    }

    public Problem(String slug) {
        this.slug = slug;
    }

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public String getDifficulty() { return difficulty; }
    public int getPosition() { return position; }
    public String getStatement() { return statement; }
    public String getInputFormat() { return inputFormat; }
    public String getOutputFormat() { return outputFormat; }
    public String getConstraints() { return constraints; }
    public String getExpectedComplexity() { return expectedComplexity; }
    public String getContentHash() { return contentHash; }
    public List<TestCase> getTestCases() { return testCases; }
    public String getInterviewerGuide() { return interviewerGuide; }

    public List<String> getHints() {
        return hints == null || hints.isEmpty() ? List.of() : List.of(hints.split(HINT_SEPARATOR));
    }

    public void setInterviewMaterial(String guide, List<String> hintList) {
        this.interviewerGuide = guide;
        this.hints = hintList == null || hintList.isEmpty() ? null : String.join(HINT_SEPARATOR, hintList);
    }

    public void update(String title, String difficulty, int position, String statement,
                       String inputFormat, String outputFormat, String constraints,
                       String expectedComplexity, String contentHash) {
        this.expectedComplexity = expectedComplexity;
        this.contentHash = contentHash;
        this.title = title;
        this.difficulty = difficulty;
        this.position = position;
        this.statement = statement;
        this.inputFormat = inputFormat;
        this.outputFormat = outputFormat;
        this.constraints = constraints;
    }
}
