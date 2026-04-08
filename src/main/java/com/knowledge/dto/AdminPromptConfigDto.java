package com.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdminPromptConfigDto {
    private String key;
    private String label;
    private String content;
    private String defaultContent;
}
