package com.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ParseJobStatusDto {
    private String jobId;
    private String status;   // PENDING | PROCESSING | DONE | FAILED
    private int progress;
    private String stage;
    private String fileName;
    private String noteId;   // 仅 DONE 时有值
    private String errorMessage; // 仅 FAILED 时有值
}
