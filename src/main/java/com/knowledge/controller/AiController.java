package com.knowledge.controller;

import com.knowledge.common.Result;
import com.knowledge.dto.ChatRequest;
import com.knowledge.dto.ParseJobStatusDto;
import com.knowledge.service.AiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    /**
     * 上传文件，立即返回 jobId，后台异步解析
     */
    @PostMapping("/parse-document")
    public ResponseEntity<Result<Map<String, String>>> parseDocument(
        @RequestParam("file") MultipartFile file,
        @RequestParam("noteName") String noteName,
        @RequestParam(value = "category", required = false) String category,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.valueOf(userDetails.getUsername());
        String jobId = aiService.startParseJob(file, noteName, userId, category);
        return ResponseEntity.ok(Result.ok(Map.of("jobId", jobId)));
    }

    /**
     * 中止进行中的解析任务（已结束的任务不可取消）
     */
    @PostMapping("/parse-cancel/{jobId}")
    public ResponseEntity<Result<Void>> cancelParseJob(
        @PathVariable String jobId,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.valueOf(userDetails.getUsername());
        aiService.cancelParseJob(jobId, userId);
        return ResponseEntity.ok(Result.ok());
    }

    /**
     * 查询单个解析任务状态
     */
    @GetMapping("/parse-status/{jobId}")
    public ResponseEntity<Result<ParseJobStatusDto>> getParseStatus(
        @PathVariable String jobId,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.valueOf(userDetails.getUsername());
        return ResponseEntity.ok(Result.ok(aiService.getParseStatus(jobId, userId)));
    }

    /**
     * 查询当前用户所有未完成的任务（页面刷新后前端恢复轮询用）
     */
    @GetMapping("/parse-status/pending")
    public ResponseEntity<Result<List<ParseJobStatusDto>>> getPendingJobs(
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.valueOf(userDetails.getUsername());
        return ResponseEntity.ok(Result.ok(aiService.getPendingJobs(userId)));
    }

    /**
     * 流式 AI 问答，返回 SSE 事件流（不套 Result 信封）
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(
        @Valid @RequestBody ChatRequest req,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.valueOf(userDetails.getUsername());
        return aiService.chat(req.getNoteId(), req.getQuestion(), userId);
    }
}
