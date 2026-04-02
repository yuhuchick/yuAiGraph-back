package com.knowledge.repository;

import com.knowledge.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoteRepository extends JpaRepository<Note, String> {
    List<Note> findByUserIdOrderByCreatedAtDesc(Long userId);
}
