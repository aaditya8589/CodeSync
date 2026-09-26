package com.codesync.backend.controller;

import com.codesync.backend.dto.CreateRoomRequest;
import com.codesync.backend.dto.RoomResponse;
import com.codesync.backend.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(
            @Valid @RequestBody CreateRoomRequest request,
            Authentication authentication
    ) {
        String username = authentication.getName();

        RoomResponse response = roomService.createRoom(
                request,
                username
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/{roomId}/join")
public ResponseEntity<Void> joinRoom(
        @PathVariable UUID roomId,
        Authentication authentication
) {
    String username = authentication.getName();

    roomService.joinRoom(
            roomId,
            username
    );

    return ResponseEntity.noContent().build();
}
@GetMapping
public ResponseEntity<List<RoomResponse>> getMyRooms(
        Authentication authentication
) {
    String username = authentication.getName();

    List<RoomResponse> rooms = roomService.getMyRooms(username);

    return ResponseEntity.ok(rooms);
}

@GetMapping("/{roomId}")
public ResponseEntity<RoomResponse> getRoom(
        @PathVariable UUID roomId,
        Authentication authentication
) {
    String username = authentication.getName();

    RoomResponse room = roomService.getRoom(roomId, username);

    return ResponseEntity.ok(room);
}
}