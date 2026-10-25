package com.codesync.backend.repository;

import com.codesync.backend.entity.InterviewEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InterviewEventRepository extends JpaRepository<InterviewEvent, UUID> {

    List<InterviewEvent> findByInterviewIdOrderByCreatedAtAsc(UUID interviewId);
}
