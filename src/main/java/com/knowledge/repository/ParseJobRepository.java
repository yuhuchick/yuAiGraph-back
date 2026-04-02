package com.knowledge.repository;

import com.knowledge.entity.ParseJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParseJobRepository extends JpaRepository<ParseJob, String> {

    Optional<ParseJob> findByIdAndUserId(String id, Long userId);

    List<ParseJob> findByUserIdAndStatusIn(Long userId, List<String> statuses);
}
