package com.codesync.backend.history;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionGroupingTest {

    private static final Instant T0 = Instant.parse("2026-10-10T10:00:00Z");

    private static VersionGrouping.Edit edit(long revision, String author, long secondsAfterStart) {
        return new VersionGrouping.Edit(revision, author, T0.plusSeconds(secondsAfterStart));
    }

    @Test
    void noEditsMeansNoVersions() {
        assertTrue(VersionGrouping.group(5, List.of()).isEmpty());
    }

    @Test
    void editsCloseTogetherAreOneVersion() {
        List<VersionGrouping.Version> versions = VersionGrouping.group(10, List.of(
                edit(11, "alice", 0),
                edit(12, "bob", 30),
                edit(13, "alice", 150)  // 2 minutes after the previous edit: still the same version
        ));

        assertEquals(List.of(new VersionGrouping.Version(
                10, 13, List.of("alice", "bob"), T0, T0.plusSeconds(150), 3)), versions);
    }

    @Test
    void aPauseLongerThanTheIdleGapStartsANewVersion() {
        List<VersionGrouping.Version> versions = VersionGrouping.group(0, List.of(
                edit(1, "alice", 0),
                edit(2, "alice", 10),
                edit(3, "bob", 10 + 121),
                edit(4, "bob", 140),
                edit(5, "carol", 1000)
        ));

        assertEquals(3, versions.size());
        assertEquals(new VersionGrouping.Version(0, 2, List.of("alice"), T0, T0.plusSeconds(10), 2), versions.get(0));
        assertEquals(new VersionGrouping.Version(2, 4, List.of("bob"), T0.plusSeconds(131), T0.plusSeconds(140), 2), versions.get(1));
        assertEquals(new VersionGrouping.Version(4, 5, List.of("carol"), T0.plusSeconds(1000), T0.plusSeconds(1000), 1), versions.get(2));
    }

    @Test
    void versionsCoverEveryRevisionWithoutGaps() {
        List<VersionGrouping.Edit> edits = new java.util.ArrayList<>();
        java.util.Random random = new java.util.Random(7);
        long seconds = 0;
        for (long revision = 41; revision <= 540; revision++) {
            seconds += random.nextInt(10) == 0 ? 600 : random.nextInt(20);
            edits.add(edit(revision, "user" + random.nextInt(3), seconds));
        }

        List<VersionGrouping.Version> versions = VersionGrouping.group(40, edits);

        long expectedFrom = 40;
        int total = 0;
        for (VersionGrouping.Version version : versions) {
            assertEquals(expectedFrom, version.fromRevision());
            assertEquals(version.toRevision() - version.fromRevision(), version.edits());
            expectedFrom = version.toRevision();
            total += version.edits();
        }
        assertEquals(540, expectedFrom);
        assertEquals(500, total);
        assertTrue(versions.size() > 10);
    }
}
