package com.codesync.backend.dto;

/** Client to server: "this connection is in the room as this tab". */
public record PresenceJoinMessage(String clientId) {
}
