package com.knowledge.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "graph_nodes", indexes = @Index(name = "idx_note_id", columnList = "note_id"))
public class GraphNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long dbId;

    /** AI 生成的节点 ID（如 n1, n2），在同一笔记内唯一 */
    @Column(name = "node_id", nullable = false, length = 50)
    private String nodeId;

    @Column(name = "note_id", nullable = false, length = 36)
    private String noteId;

    @Column(nullable = false, length = 200)
    private String name;

    /** concept / person / event / object */
    @Column(nullable = false, length = 20)
    private String type;

    @Column(columnDefinition = "TEXT")
    private String description;
}
