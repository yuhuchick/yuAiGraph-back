package com.knowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AdminOverviewDto {
    private long totalUsers;
    private long adminUsers;
    private long totalNotes;
    private List<UserInfo> recentUsers;
}
