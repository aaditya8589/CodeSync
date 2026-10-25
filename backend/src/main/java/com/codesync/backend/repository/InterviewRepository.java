package com.codesync.backend.repository;

import com.codesync.backend.entity.Interview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterviewRepository extends JpaRepository<Interview, UUID> {

    Optional<Interview> findFirstByRoomId(UUID roomId);

    List<Interview> findTop50ByInterviewerUsernameOrCandidateUsernameOrderByCreatedAtDesc(
            String interviewerUsername, String candidateUsername);
}
