package com.knowledge.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "graph_links", indexes = @Index(name = "idx_link_note_id", columnList = "note_id"))
public class GraphLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "note_id", nullable = false, length = 36)
    private String noteId;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(nullable = false, length = 50)
    private String target;

    @Column(length = 100)
    private String relationship;
}
