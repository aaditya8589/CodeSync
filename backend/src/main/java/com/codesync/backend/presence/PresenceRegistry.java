package com.codesync.backend.presence;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is connected to which room, kept in memory: presence only matters while a connection
 * is open, so it does not belong in the database.
 *
 * <p>Rooms are marked as authorized for a WebSocket session when its subscription passes the
 * membership check. Cursor messages are then checked against this map instead of the database,
 * because cursors move far more often than the document changes.
 */
@Component
public class PresenceRegistry {

    public static final int MAX_CLIENT_ID_LENGTH = 64;

    public record Member(String username, List<String> clientIds) {
    }

    private static final class Session {
        final String username;
        final Set<UUID> authorizedRooms = ConcurrentHashMap.newKeySet();
        final Map<UUID, String> clientIds = new ConcurrentHashMap<>();

        Session(String username) {
            this.username = username;
        }
    }

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public void authorize(String sessionId, String username, UUID roomId) {
        Session session = sessions.computeIfAbsent(sessionId, id -> new Session(username));
        if (!session.username.equals(username)) {
            throw new IllegalStateException("WebSocket session changed user");
        }
        session.authorizedRooms.add(roomId);
    }

    public boolean isAuthorized(String sessionId, UUID roomId) {
        Session session = sessions.get(sessionId);
        return session != null && session.authorizedRooms.contains(roomId);
    }

    /** Records which tab (clientId) this session is in the room as. False if not authorized. */
    public boolean join(String sessionId, UUID roomId, String clientId) {
        if (clientId == null || clientId.isEmpty() || clientId.length() > MAX_CLIENT_ID_LENGTH) {
            return false;
        }
        Session session = sessions.get(sessionId);
        if (session == null || !session.authorizedRooms.contains(roomId)) {
            return false;
        }
        session.clientIds.put(roomId, clientId);
        return true;
    }

    /** The clientId this session joined the room with, or null. Never taken from the client's message. */
    public String clientIdOf(String sessionId, UUID roomId) {
        Session session = sessions.get(sessionId);
        return session == null ? null : session.clientIds.get(roomId);
    }

    public String usernameOf(String sessionId) {
        Session session = sessions.get(sessionId);
        return session == null ? null : session.username;
    }

    /** Forgets a closed session and returns the rooms whose presence changed. */
    public Set<UUID> remove(String sessionId) {
        Session session = sessions.remove(sessionId);
        return session == null ? Set.of() : Set.copyOf(session.clientIds.keySet());
    }

    /** Everyone in the room, one entry per user, with one clientId per open tab. */
    public List<Member> members(UUID roomId) {
        Map<String, List<String>> byUser = new TreeMap<>();
        for (Session session : sessions.values()) {
            String clientId = session.clientIds.get(roomId);
            if (clientId != null) {
                byUser.computeIfAbsent(session.username, name -> new ArrayList<>()).add(clientId);
            }
        }

        List<Member> members = new ArrayList<>();
        byUser.forEach((username, clientIds) -> {
            clientIds.sort(Comparator.naturalOrder());
            members.add(new Member(username, List.copyOf(clientIds)));
        });
        return members;
    }
}
