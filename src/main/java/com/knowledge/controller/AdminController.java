package com.knowledge.controller;

import com.knowledge.common.Result;
import com.knowledge.dto.AdminOverviewDto;
import com.knowledge.dto.AdminPromptConfigDto;
import com.knowledge.dto.AdminPromptConfigUpdateRequest;
import com.knowledge.dto.AdminUserCreateRequest;
import com.knowledge.dto.AdminUserUpdateRequest;
import com.knowledge.dto.PageDto;
import com.knowledge.dto.UserInfo;
import com.knowledge.service.AdminService;
import com.knowledge.service.PromptConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final PromptConfigService promptConfigService;

    @GetMapping("/overview")
    public ResponseEntity<Result<AdminOverviewDto>> overview() {
        return ResponseEntity.ok(Result.ok(adminService.overview()));
    }

    @GetMapping("/users")
    public ResponseEntity<Result<PageDto<UserInfo>>> listUsers(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String role
    ) {
        return ResponseEntity.ok(Result.ok(adminService.listUsers(page, size, keyword, role)));
    }

    @PostMapping("/users")
    public ResponseEntity<Result<UserInfo>> createUser(@Valid @RequestBody AdminUserCreateRequest req) {
        return ResponseEntity.ok(Result.ok(adminService.createUser(req)));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<Result<UserInfo>> updateUser(
        @PathVariable Long id,
        @Valid @RequestBody AdminUserUpdateRequest req
    ) {
        return ResponseEntity.ok(Result.ok(adminService.updateUser(id, req)));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Result<Void>> deleteUser(
        @PathVariable Long id,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long currentUserId = Long.valueOf(userDetails.getUsername());
        adminService.deleteUser(id, currentUserId);
        return ResponseEntity.ok(Result.ok());
    }

    @GetMapping("/prompts")
    public ResponseEntity<Result<java.util.List<AdminPromptConfigDto>>> listPrompts() {
        return ResponseEntity.ok(Result.ok(promptConfigService.listConfigs()));
    }

    @PutMapping("/prompts/{key}")
    public ResponseEntity<Result<AdminPromptConfigDto>> updatePrompt(
        @PathVariable String key,
        @Valid @RequestBody AdminPromptConfigUpdateRequest req
    ) {
        return ResponseEntity.ok(Result.ok(promptConfigService.updateConfig(key, req.getContent())));
    }
}
