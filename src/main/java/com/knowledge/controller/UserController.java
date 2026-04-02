package com.knowledge.controller;

import com.knowledge.common.Result;
import com.knowledge.dto.AuthResponse;
import com.knowledge.dto.LoginRequest;
import com.knowledge.dto.RegisterRequest;
import com.knowledge.dto.UserInfo;
import com.knowledge.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<Result<AuthResponse>> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(Result.ok(userService.register(req)));
    }

    @PostMapping("/login")
    public ResponseEntity<Result<AuthResponse>> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(Result.ok(userService.login(req)));
    }

    @GetMapping("/me")
    public ResponseEntity<Result<UserInfo>> me(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.valueOf(userDetails.getUsername());
        return ResponseEntity.ok(Result.ok(userService.getMe(userId)));
    }
}
