package com.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteListResponse {
    private List<NoteItem> items;
    /** 当前筛选条件下的总条数 */
    private long total;
    private int page;
    private int size;
    private int totalPages;
    /** 用户全部笔记数（不受筛选影响，供仪表盘） */
    private long allNotesCount;
    /** 用户全部节点数 */
    private long totalNodeCount;
    /** 本月新建笔记数 */
    private long notesThisMonth;
}
