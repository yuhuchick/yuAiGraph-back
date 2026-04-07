package com.knowledge.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "notes")
public class Note {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** 用户自定义分类，空字符串表示其他 */
    @Column(length = 64)
    private String category = "";

    /** AI 生成的多视角图表 JSON 数组（InsightChartSpecDto[]） */
    @Column(name = "insights_json", columnDefinition = "LONGTEXT")
    private String insightsJson;
}
