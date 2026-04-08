package com.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AdminPromptConfigUpdateRequest {
    @NotBlank(message = "提示词内容不能为空")
    private String content;
}
