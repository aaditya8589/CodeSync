package com.codesync.backend.service;
import java.util.UUID;
import com.codesync.backend.dto.CreateRoomRequest;
import com.codesync.backend.dto.RoomResponse;
import com.codesync.backend.entity.Room;
import com.codesync.backend.entity.RoomMember;
import com.codesync.backend.entity.User;
import com.codesync.backend.repository.RoomMemberRepository;
import com.codesync.backend.repository.RoomRepository;
import com.codesync.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.codesync.backend.exception.AlreadyMemberException;
import com.codesync.backend.exception.RoomAccessDeniedException;
import com.codesync.backend.exception.RoomNotFoundException;
import java.util.List;



@Service
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;

    public RoomService(
            RoomRepository roomRepository,
            RoomMemberRepository roomMemberRepository,
            UserRepository userRepository
    ) {
        this.roomRepository = roomRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public RoomResponse createRoom(
            CreateRoomRequest request,
            String username
    ) {
        User owner = userRepository
                .findByUsername(username)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found")
                );

        Room room = new Room(request.getName(), owner);
        Room savedRoom = roomRepository.save(room);

        RoomMember ownerMembership = new RoomMember(
                savedRoom,
                owner,
                "OWNER"
        );

        roomMemberRepository.save(ownerMembership);

        return new RoomResponse(
                savedRoom.getId(),
                savedRoom.getName(),
                owner.getId(),
                savedRoom.getCreatedAt()
        );
    }
    @Transactional
public void joinRoom(
        UUID roomId,
        String username
) {
    User user = userRepository
            .findByUsername(username)
            .orElseThrow(() ->
                    new IllegalArgumentException("User not found")
            );

    Room room = roomRepository
            .findById(roomId)
            .orElseThrow(() ->
                    new IllegalArgumentException("Room not found")
            );

    if (roomMemberRepository.existsByRoomIdAndUserId(
            roomId,
            user.getId()
    )) {
        throw new AlreadyMemberException(
        "User is already a member of this room"
);
    }

    RoomMember membership = new RoomMember(
            room,
            user,
            "MEMBER"
    );

    roomMemberRepository.save(membership);
}

@Transactional(readOnly = true)
public List<RoomResponse> getMyRooms(String username) {

    User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    return roomMemberRepository.findByUserId(user.getId())
            .stream()
            .map(member -> new RoomResponse(
                    member.getRoom().getId(),
                    member.getRoom().getName(),
                    member.getRoom().getOwner().getId(),
                    member.getRoom().getCreatedAt()
            ))
            .toList();
}

@Transactional(readOnly = true)
public void checkRoomAccess(UUID roomId, String username) {

    User user = userRepository.findByUsername(username)
            .orElseThrow(() ->
                    new IllegalArgumentException("User not found")
            );

    if (!roomRepository.existsById(roomId)) {
    throw new RoomNotFoundException("Room not found");
}

    boolean isMember =
            roomMemberRepository.existsByRoomIdAndUserId(
                    roomId,
                    user.getId()
            );

    if (!isMember) {
        throw new RoomAccessDeniedException(
                "You are not a member of this room"
        );
    }
}

@Transactional(readOnly = true)
public RoomResponse getRoom(UUID roomId, String username) {

    checkRoomAccess(roomId, username);

    Room room = roomRepository
        .findById(roomId)
        .orElseThrow(() ->
                new RoomNotFoundException("Room not found")
        );

    return new RoomResponse(
            room.getId(),
            room.getName(),
            room.getOwner().getId(),
            room.getCreatedAt()
    );
}
}