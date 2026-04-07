package com.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NoteItem {
    private String id;
    private String name;
    private String createdAt;
    private int nodeCount;
    /** 分类，空表示其他 */
    private String category;
}
