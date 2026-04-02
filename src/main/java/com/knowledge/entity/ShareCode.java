package com.knowledge.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "share_codes")
public class ShareCode {

    @Id
    @Column(length = 36)
    private String code;

    @Column(name = "note_id", nullable = false, length = 36)
    private String noteId;

    /** view / edit */
    @Column(nullable = false, length = 10)
    private String permission;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
