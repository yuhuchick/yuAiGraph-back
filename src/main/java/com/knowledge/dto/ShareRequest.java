package com.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ShareRequest {

    @NotBlank(message = "permission 不能为空")
    @Pattern(regexp = "view|edit", message = "permission 只能为 view 或 edit")
    private String permission;
}
