package com.knowledge.repository;

import com.knowledge.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NoteRepository extends JpaRepository<Note, String>, JpaSpecificationExecutor<Note> {
    List<Note> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserId(Long userId);

    long countByUserIdAndCreatedAtGreaterThanEqual(Long userId, LocalDateTime from);

    @Query("SELECT DISTINCT n.category FROM Note n WHERE n.userId = :userId AND n.category IS NOT NULL AND n.category <> '' ORDER BY n.category")
    List<String> findDistinctCategoriesByUserId(@Param("userId") Long userId);
}
