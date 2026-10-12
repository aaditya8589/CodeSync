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
    public List<TestCase> getTestCases() { return testCases; }

    public void update(String title, String difficulty, int position, String statement,
                       String inputFormat, String outputFormat, String constraints) {
        this.title = title;
        this.difficulty = difficulty;
        this.position = position;
        this.statement = statement;
        this.inputFormat = inputFormat;
        this.outputFormat = outputFormat;
        this.constraints = constraints;
    }
}
