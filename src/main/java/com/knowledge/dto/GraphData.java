package com.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GraphData {
    private List<GraphNodeDto> nodes;
    private List<GraphLinkDto> links;
    /** 文档语义驱动的多视角图表（可为空；旧数据兼容） */
    private List<InsightChartSpecDto> insightCharts;
}
