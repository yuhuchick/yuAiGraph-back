package com.knowledge.repository;

import com.knowledge.entity.GraphNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GraphNodeRepository extends JpaRepository<GraphNode, Long> {
    List<GraphNode> findByNoteId(String noteId);
    void deleteByNoteId(String noteId);

    @Query("SELECT COUNT(g) FROM GraphNode g WHERE g.noteId IN (SELECT n.id FROM Note n WHERE n.userId = :userId)")
    long countNodesForUser(@Param("userId") Long userId);
}
