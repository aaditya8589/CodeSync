package com.codesync.backend.controller;

import com.codesync.backend.dto.CursorMessage;
import com.codesync.backend.dto.PresenceJoinMessage;
import com.codesync.backend.dto.PresenceMessage;
import com.codesync.backend.dto.RemoteCursorMessage;
import com.codesync.backend.presence.PresenceRegistry;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresenceControllerTest {

    private static final UUID ROOM = UUID.fromString("038ceccb-2f6b-41f4-b979-cf56cbcc5573");
    private static final String DOCUMENT = "aaaaaaaa-0000-0000-0000-000000000001";

    // Everything the controller publishes lands here instead of in a broker
    private final List<Message<?>> sent = new ArrayList<>();
    private final PresenceRegistry registry = new PresenceRegistry();
    private final PresenceController controller = new PresenceController(
            new SimpMessagingTemplate((message, timeout) -> sent.add(message)),
            registry
    );

    private static SimpMessageHeaderAccessor session(String sessionId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create();
        accessor.setSessionId(sessionId);
        return accessor;
    }

    private static String destination(Message<?> message) {
        return SimpMessageHeaderAccessor.getDestination(message.getHeaders());
    }

    private void joined(String sessionId, String username, String clientId) {
        registry.authorize(sessionId, username, ROOM);
        controller.join(ROOM.toString(), new PresenceJoinMessage(clientId), session(sessionId));
        sent.clear();
    }

    @Test
    void joinWithoutAuthorizedSubscriptionIsIgnored() {
        controller.join(ROOM.toString(), new PresenceJoinMessage("tab-1"), session("s1"));
        assertTrue(sent.isEmpty());
    }

    @Test
    void joinBroadcastsMemberList() {
        registry.authorize("s1", "alice", ROOM);
        controller.join(ROOM.toString(), new PresenceJoinMessage("tab-1"), session("s1"));

        assertEquals(1, sent.size());
        assertEquals("/topic/rooms/" + ROOM + "/presence", destination(sent.get(0)));
        PresenceMessage presence = (PresenceMessage) sent.get(0).getPayload();
        assertEquals(List.of(new PresenceRegistry.Member("alice", List.of("tab-1"))), presence.members());
    }

    @Test
    void cursorIsBroadcastWithTheServerKnownIdentity() {
        joined("s1", "alice", "tab-1");

        controller.cursor(ROOM.toString(), new CursorMessage(DOCUMENT, 7L, 3, 9), session("s1"));

        assertEquals(1, sent.size());
        assertEquals("/topic/rooms/" + ROOM + "/cursors", destination(sent.get(0)));
        assertEquals(
                new RemoteCursorMessage("tab-1", "alice", DOCUMENT, 7, 3, 9),
                sent.get(0).getPayload()
        );
    }

    @Test
    void cursorFromSessionThatDidNotJoinIsDropped() {
        registry.authorize("s1", "alice", ROOM);
        controller.cursor(ROOM.toString(), new CursorMessage(DOCUMENT, 0L, 0, 0), session("s1"));
        controller.cursor(ROOM.toString(), new CursorMessage(DOCUMENT, 0L, 0, 0), session("stranger"));
        assertTrue(sent.isEmpty());
    }

    @Test
    void cursorForAnotherRoomIsDropped() {
        joined("s1", "alice", "tab-1");
        controller.cursor(UUID.randomUUID().toString(), new CursorMessage(DOCUMENT, 0L, 0, 0), session("s1"));
        controller.cursor("not-a-uuid", new CursorMessage(DOCUMENT, 0L, 0, 0), session("s1"));
        assertTrue(sent.isEmpty());
    }

    @Test
    void invalidCursorsAreDropped() {
        joined("s1", "alice", "tab-1");
        String room = ROOM.toString();

        controller.cursor(room, null, session("s1"));
        controller.cursor(room, new CursorMessage(null, 0L, 0, 0), session("s1"));
        controller.cursor(room, new CursorMessage("not-a-uuid", 0L, 0, 0), session("s1"));
        controller.cursor(room, new CursorMessage(DOCUMENT, null, 0, 0), session("s1"));
        controller.cursor(room, new CursorMessage(DOCUMENT, -1L, 0, 0), session("s1"));
        controller.cursor(room, new CursorMessage(DOCUMENT, 0L, -1, 0), session("s1"));
        controller.cursor(room, new CursorMessage(DOCUMENT, 0L, 0, 500_001), session("s1"));

        assertTrue(sent.isEmpty());
    }

    @Test
    void disconnectBroadcastsUpdatedMemberList() {
        joined("s1", "alice", "tab-1");
        joined("s2", "bob", "tab-2");

        controller.onDisconnect(new SessionDisconnectEvent(
                this, MessageBuilder.withPayload(new byte[0]).build(), "s1", CloseStatus.NORMAL));

        assertEquals(1, sent.size());
        PresenceMessage presence = (PresenceMessage) sent.get(0).getPayload();
        assertEquals(List.of(new PresenceRegistry.Member("bob", List.of("tab-2"))), presence.members());
    }

    @Test
    void disconnectOfSessionNotInAnyRoomSendsNothing() {
        controller.onDisconnect(new SessionDisconnectEvent(
                this, MessageBuilder.withPayload(new byte[0]).build(), "nobody", CloseStatus.NORMAL));
        assertTrue(sent.isEmpty());
    }
}
