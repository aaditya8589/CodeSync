package com.codesync.backend.entity;

import jakarta.persistence.*;

import java.util.UUID;

/** One input and its expected output. Samples are shown to users; the rest stay hidden. */
@Entity
@Table(name = "test_cases")
public class TestCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private boolean sample;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String input;

    @Column(name = "expected_output", nullable = false, columnDefinition = "TEXT")
    private String expectedOutput;

    public TestCase() {
    }

    public TestCase(Problem problem, int position, boolean sample, String input, String expectedOutput) {
        this.problem = problem;
        this.position = position;
        this.sample = sample;
        this.input = input;
        this.expectedOutput = expectedOutput;
    }

    public int getPosition() { return position; }
    public boolean isSample() { return sample; }
    public String getInput() { return input; }
    public String getExpectedOutput() { return expectedOutput; }
}
