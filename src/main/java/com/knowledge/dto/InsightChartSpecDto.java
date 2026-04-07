package com.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 由 AI 根据文档内容选择的「重点知识」可视化规格（非图谱结构统计）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InsightChartSpecDto {
    private String id;
    private String title;
    /** 为何选用该图类型、对应文档中哪类信息 */
    private String rationale;
    /**
     * pie | bar | line | radar | scatter | table
     * 由模型按内容语义选择，勿滥用；无合适数据时不要生成该项。
     */
    private String chartType;
    /** X 轴类目 / 饼图扇区名称 / 雷达维度名 */
    private List<String> categories;
    /** 一个或多个系列（多系列柱状/折线） */
    private List<InsightSeriesDto> series;
    /**
     * 仅 chartType=scatter 时使用：[[x1,y1],[x2,y2],...]，与 categories 二选一为主数据
     */
    private List<List<Double>> scatterPoints;
    /** chartType=table 时：表头 */
    private List<String> tableColumns;
    /** chartType=table 时：数据行，每行单元格与 tableColumns 等长 */
    private List<List<String>> tableRows;
}
