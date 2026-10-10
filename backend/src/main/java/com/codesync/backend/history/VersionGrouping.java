package com.codesync.backend.history;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns the per-keystroke operation log into versions a person would recognise: edits
 * belong to the same version until nobody has typed for {@link #IDLE_GAP}.
 */
public final class VersionGrouping {

    public static final Duration IDLE_GAP = Duration.ofMinutes(2);

    public record Edit(long revision, String author, Instant createdAt) {
    }

    /**
     * fromRevision is the state before the first edit of the version, toRevision the state
     * after its last edit, so "view this version" means revision toRevision.
     */
    public record Version(
            long fromRevision,
            long toRevision,
            List<String> authors,
            Instant startedAt,
            Instant endedAt,
            int edits
    ) {
    }

    private VersionGrouping() {
    }

    /** Edits must be in revision order and directly follow {@code startRevision}. Oldest first. */
    public static List<Version> group(long startRevision, List<Edit> edits) {
        List<Version> versions = new ArrayList<>();

        long from = startRevision;
        Edit first = null;
        Edit previous = null;
        Set<String> authors = new LinkedHashSet<>();
        int count = 0;

        for (Edit edit : edits) {
            if (previous != null && Duration.between(previous.createdAt(), edit.createdAt()).compareTo(IDLE_GAP) > 0) {
                versions.add(new Version(from, previous.revision(), List.copyOf(authors),
                        first.createdAt(), previous.createdAt(), count));
                from = previous.revision();
                first = null;
                authors.clear();
                count = 0;
            }
            if (first == null) first = edit;
            authors.add(edit.author());
            count++;
            previous = edit;
        }

        if (previous != null) {
            versions.add(new Version(from, previous.revision(), List.copyOf(authors),
                    first.createdAt(), previous.createdAt(), count));
        }
        return versions;
    }
}
