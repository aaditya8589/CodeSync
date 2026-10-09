package com.codesync.backend.execution;

public enum ExecutionStatus {
    SUCCESS,
    COMPILE_ERROR,
    RUNTIME_ERROR,
    TIME_LIMIT_EXCEEDED,
    MEMORY_LIMIT_EXCEEDED,
    INTERNAL_ERROR
}
