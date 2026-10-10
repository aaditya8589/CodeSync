package com.codesync.backend.dto;

import com.codesync.backend.presence.PresenceRegistry;

import java.util.List;

/** Server to the room: the full list of who is connected, sent whenever it changes. */
public record PresenceMessage(List<PresenceRegistry.Member> members) {
}
