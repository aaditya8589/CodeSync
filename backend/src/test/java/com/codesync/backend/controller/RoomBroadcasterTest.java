package com.codesync.backend.controller;

import com.codesync.backend.dto.FileChangeMessage;
import com.codesync.backend.ot.TextOperation;
import com.codesync.backend.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Where edits go: the whole room, or only the owner of a private match file. */
class RoomBroadcasterTest {

    private static final UUID ROOM = UUID.fromString("038ceccb-2f6b-41f4-b979-cf56cbcc5573");

    private final List<Message<?>> sent = new ArrayList<>();
    private final RoomBroadcaster broadcaster = new RoomBroadcaster(
            new SimpMessagingTemplate((message, timeout) -> sent.add(message)));

    private List<String> destinations() {
        return sent.stream().map(message -> SimpMessageHeaderAccessor.getDestination(message.getHeaders())).toList();
    }

    private static DocumentService.AppliedOperation edit(String privateTo) {
        return new DocumentService.AppliedOperation(UUID.randomUUID(), 3, new TextOperation().insert("x"), privateTo);
    }

    @Test
    void sharedEditsGoToTheRoom() {
        broadcaster.operation(ROOM, edit(null), "tab-1", "ada");
        assertEquals(List.of("/topic/rooms/" + ROOM + "/code"), destinations());
    }

    @Test
    void privateEditsGoOnlyToTheOwner() {
        broadcaster.operation(ROOM, edit("ada"), "tab-1", "ada");
        assertEquals(List.of("/user/ada/queue/rooms/" + ROOM + "/code"), destinations());
    }

    @Test
    void privateRenamesGoOnlyToTheOwner() {
        FileChangeMessage rename = new FileChangeMessage(FileChangeMessage.Type.RENAMED, "id", "A-ada.py", "A-ada.cpp", "ada");
        broadcaster.fileChange(ROOM, rename, "ada");
        broadcaster.fileChange(ROOM, rename, null);
        assertEquals(List.of("/user/ada/queue/rooms/" + ROOM + "/files", "/topic/rooms/" + ROOM + "/files"), destinations());
    }
}
