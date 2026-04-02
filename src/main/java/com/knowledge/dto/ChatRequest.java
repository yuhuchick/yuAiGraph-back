package com.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatRequest {

    @NotBlank(message = "笔记 ID 不能为空")
    private String noteId;

    @NotBlank(message = "问题不能为空")
    private String question;
}
