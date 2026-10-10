package com.codesync.backend.dto;

import com.codesync.backend.history.VersionGrouping;

import java.util.List;

/**
 * historyStartRevision is the oldest revision that can be viewed (null if the document has
 * not been edited since history was added). Versions are newest first.
 */
public record HistoryResponse(
        String documentId,
        String fileName,
        long currentRevision,
        Long historyStartRevision,
        List<VersionGrouping.Version> versions
) {
}
