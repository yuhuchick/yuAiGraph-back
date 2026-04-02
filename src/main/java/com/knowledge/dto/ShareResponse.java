package com.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ShareResponse {
    private String shareCode;
    private String shareUrl;
    private String permission;
}
