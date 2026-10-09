package com.codesync.backend.execution;

public record ExecutionResult(
        ExecutionStatus status,
        String stdout,
        String stderr,
        Integer exitCode,
        long durationMs,
        boolean outputTruncated
) {
}
