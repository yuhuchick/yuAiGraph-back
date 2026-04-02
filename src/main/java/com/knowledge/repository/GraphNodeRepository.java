package com.knowledge.repository;

import com.knowledge.entity.GraphNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GraphNodeRepository extends JpaRepository<GraphNode, Long> {
    List<GraphNode> findByNoteId(String noteId);
    void deleteByNoteId(String noteId);
}
