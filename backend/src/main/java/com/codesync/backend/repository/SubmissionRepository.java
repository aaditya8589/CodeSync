package com.codesync.backend.repository;

import com.codesync.backend.entity.Submission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    List<Submission> findTop20ByProblemIdAndUsernameOrderByCreatedAtDesc(UUID problemId, String username);

    @Query("select distinct s.problem.slug from Submission s where s.username = :username and s.verdict = 'ACCEPTED'")
    List<String> findSolvedSlugs(@Param("username") String username);

    boolean existsByProblemIdAndUsernameAndVerdict(UUID problemId, String username, String verdict);

    List<Submission> findByMatchIdOrderByCreatedAtAsc(UUID matchId);

    Optional<Submission> findFirstByProblemIdAndUsernameOrderByCreatedAtDesc(UUID problemId, String username);

    /** Per problem the user tried: slug, attempts, accepted attempts, first accepted time. */
    @Query("select s.problem.slug, count(s), sum(case when s.verdict = 'ACCEPTED' then 1 else 0 end),"
            + " min(case when s.verdict = 'ACCEPTED' then s.createdAt end)"
            + " from Submission s where s.username = :username group by s.problem.slug")
    List<Object[]> summarizeByProblem(@Param("username") String username);
}
