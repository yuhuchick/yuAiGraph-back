package com.knowledge.controller;

import com.knowledge.common.Result;
import com.knowledge.dto.ShareRequest;
import com.knowledge.dto.ShareResponse;
import com.knowledge.dto.SharedGraphResponse;
import com.knowledge.service.ShareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    @PostMapping("/notes/{noteId}/share")
    public ResponseEntity<Result<ShareResponse>> createShare(
        @PathVariable String noteId,
        @Valid @RequestBody ShareRequest req,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.valueOf(userDetails.getUsername());
        return ResponseEntity.ok(Result.ok(shareService.createShare(noteId, req.getPermission(), userId)));
    }

    @GetMapping("/share/{shareCode}")
    public ResponseEntity<Result<SharedGraphResponse>> getSharedGraph(@PathVariable String shareCode) {
        return ResponseEntity.ok(Result.ok(shareService.getSharedGraph(shareCode)));
    }
}
