package com.codesync.backend.dto;

/** Optional body of a run request. stdin is what the program reads as its input. */
public record RunRequest(String stdin) {
}
