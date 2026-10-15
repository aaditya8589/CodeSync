package com.codesync.backend.repository;

import com.codesync.backend.entity.Problem;
import com.codesync.backend.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProblemRepository extends JpaRepository<Problem, UUID> {

    Optional<Problem> findBySlug(String slug);

    List<Problem> findAllByOrderByPositionAsc();

    // A problem's hidden tests can hold megabytes, so showing its statement reads only the examples
    @Query("select t from TestCase t where t.problem.id = :problemId and t.sample = true order by t.position")
    List<TestCase> findSamples(@Param("problemId") UUID problemId);

    @Query("select count(t) from TestCase t where t.problem.id = :problemId")
    long countTests(@Param("problemId") UUID problemId);
}
