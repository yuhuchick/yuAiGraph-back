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
}
