package com.knowledge.repository;

import com.knowledge.entity.GraphLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GraphLinkRepository extends JpaRepository<GraphLink, Long> {
    List<GraphLink> findByNoteId(String noteId);
    void deleteByNoteId(String noteId);
}
