package com.codesync.backend.presence;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresenceRegistryTest {

    private static final UUID ROOM = UUID.fromString("038ceccb-2f6b-41f4-b979-cf56cbcc5573");
    private static final UUID OTHER_ROOM = UUID.fromString("ad35e907-be64-4c5d-8472-112a9479e1c5");

    @Test
    void cannotJoinWithoutAnAuthorizedSubscription() {
        PresenceRegistry registry = new PresenceRegistry();
        assertFalse(registry.join("s1", ROOM, "tab-1"));

        registry.authorize("s1", "alice", OTHER_ROOM);
        assertFalse(registry.join("s1", ROOM, "tab-1"));
        assertFalse(registry.isAuthorized("s1", ROOM));
    }

    @Test
    void joinedSessionAppearsAsMember() {
        PresenceRegistry registry = new PresenceRegistry();
        registry.authorize("s1", "alice", ROOM);

        assertTrue(registry.join("s1", ROOM, "tab-1"));
        assertEquals(List.of(new PresenceRegistry.Member("alice", List.of("tab-1"))), registry.members(ROOM));
        assertEquals("tab-1", registry.clientIdOf("s1", ROOM));
        assertEquals("alice", registry.usernameOf("s1"));
    }

    @Test
    void twoTabsOfTheSameUserAreOneMemberWithTwoClients() {
        PresenceRegistry registry = new PresenceRegistry();
        registry.authorize("s1", "alice", ROOM);
        registry.authorize("s2", "alice", ROOM);
        registry.authorize("s3", "bob", ROOM);
        registry.join("s1", ROOM, "tab-b");
        registry.join("s2", ROOM, "tab-a");
        registry.join("s3", ROOM, "tab-c");

        assertEquals(List.of(
                new PresenceRegistry.Member("alice", List.of("tab-a", "tab-b")),
                new PresenceRegistry.Member("bob", List.of("tab-c"))
        ), registry.members(ROOM));
    }

    @Test
    void removingASessionReportsItsRoomsAndDropsIt() {
        PresenceRegistry registry = new PresenceRegistry();
        registry.authorize("s1", "alice", ROOM);
        registry.join("s1", ROOM, "tab-1");

        assertEquals(Set.of(ROOM), registry.remove("s1"));
        assertEquals(List.of(), registry.members(ROOM));
        assertFalse(registry.isAuthorized("s1", ROOM));
        assertNull(registry.clientIdOf("s1", ROOM));
        assertEquals(Set.of(), registry.remove("s1"));
    }

    @Test
    void sessionOnlyAuthorizedButNotJoinedChangesNoPresence() {
        PresenceRegistry registry = new PresenceRegistry();
        registry.authorize("s1", "alice", ROOM);
        assertEquals(Set.of(), registry.remove("s1"));
    }

    @Test
    void invalidClientIdsAreRejected() {
        PresenceRegistry registry = new PresenceRegistry();
        registry.authorize("s1", "alice", ROOM);
        assertFalse(registry.join("s1", ROOM, null));
        assertFalse(registry.join("s1", ROOM, ""));
        assertFalse(registry.join("s1", ROOM, "x".repeat(65)));
    }

    @Test
    void aSessionCannotSwitchUser() {
        PresenceRegistry registry = new PresenceRegistry();
        registry.authorize("s1", "alice", ROOM);
        try {
            registry.authorize("s1", "mallory", OTHER_ROOM);
            throw new AssertionError("expected an exception");
        } catch (IllegalStateException expected) {
            assertFalse(registry.isAuthorized("s1", OTHER_ROOM));
        }
    }
}
