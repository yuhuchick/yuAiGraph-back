package com.knowledge.controller;

import com.knowledge.common.Result;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping(value = {"/", "/healthz"})
    public ResponseEntity<Result<Map<String, String>>> health() {
        return ResponseEntity.ok(
            Result.ok(Map.of(
                "status", "ok"
            ))
        );
    }
}

