package com.knowledge.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "parse_jobs")
public class ParseJob {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "note_name", nullable = false, length = 200)
    private String noteName;

    /** PENDING | PROCESSING | DONE | FAILED */
    @Column(nullable = false, length = 20)
    private String status = "PENDING";

    /** 0-100 */
    @Column(nullable = false)
    private int progress = 0;

    /** 解析成功后填入 */
    @Column(name = "note_id", length = 36)
    private String noteId;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}
