package com.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SharedGraphResponse {
    private String noteName;
    private String permission;
    private GraphData graph;
}
