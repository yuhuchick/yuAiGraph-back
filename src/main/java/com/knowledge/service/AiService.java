package com.knowledge.service;

import com.knowledge.dto.GraphData;
import com.knowledge.dto.ParseJobStatusDto;
import com.knowledge.entity.ParseJob;
import com.knowledge.exception.BusinessException;
import com.knowledge.repository.GraphNodeRepository;
import com.knowledge.repository.NoteRepository;
import com.knowledge.repository.ParseJobRepository;
import com.knowledge.util.AiClient;
import com.knowledge.util.FileParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiService {

    private final FileParser fileParser;
    private final AiClient aiClient;
    private final NoteService noteService;
    private final NoteRepository noteRepository;
    private final GraphNodeRepository graphNodeRepository;
    private final ParseJobRepository parseJobRepository;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private static final String CHAT_SYSTEM_TEMPLATE = """
        你是一个知识图谱问答助手。以下是用户的知识图谱内容：
        
        %s
        
        请根据以上图谱中的实体和关系回答用户的问题。
        回答要简洁清晰，优先引用图谱中的实体和关系，如有必要可适当补充相关知识。""";

    // ─── 异步解析 ─────────────────────────────────────────────────

    /**
     * 立即返回 jobId，后台异步执行解析
     */
    public String startParseJob(MultipartFile file, String noteName, Long userId) {
        if (file.isEmpty()) throw BusinessException.badRequest("文件不能为空");
        if (file.getSize() > 100L * 1024 * 1024) throw BusinessException.badRequest("文件超过 100MB 限制");

        String jobId = UUID.randomUUID().toString();
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "未知文件";

        // 提前读取字节，防止异步线程中流已关闭
        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (Exception e) {
            throw new RuntimeException("读取文件失败: " + e.getMessage());
        }

        ParseJob job = new ParseJob();
        job.setId(jobId);
        job.setUserId(userId);
        job.setFileName(fileName);
        job.setNoteName(noteName);
        job.setStatus("PENDING");
        job.setProgress(0);
        parseJobRepository.save(job);

        String contentType = file.getContentType();

        executor.submit(() -> runParseJob(jobId, fileBytes, contentType, fileName, noteName, userId));

        log.info("已创建解析任务 jobId={}, 文件={}, 用户={}", jobId, fileName, userId);
        return jobId;
    }

    private void runParseJob(String jobId, byte[] fileBytes, String contentType,
                             String fileName, String noteName, Long userId) {
        try {
            updateJob(jobId, "PROCESSING", 10, null, null);

            log.info("[{}] 开始提取文本", jobId);
            String text = fileParser.extractText(fileBytes, contentType, fileName);
            if (text.isBlank()) {
                updateJob(jobId, "FAILED", 0, null, "文件内容为空，无法解析");
                return;
            }
            updateJob(jobId, "PROCESSING", 25, null, null);

            log.info("[{}] 文本提取完成（{}字），开始 AI 分析", jobId, text.length());
            GraphData graphData = aiClient.extractGraphData(text);

            if (graphData.getNodes() == null || graphData.getNodes().isEmpty()) {
                updateJob(jobId, "FAILED", 0, null, "AI 未能从文件中提取到有效实体，请检查文件内容");
                return;
            }
            updateJob(jobId, "PROCESSING", 85, null, null);

            log.info("[{}] AI 分析完成，节点数={}，开始保存", jobId, graphData.getNodes().size());
            String noteId = noteService.saveGraph(noteName, userId, graphData);
            updateJob(jobId, "DONE", 100, noteId, null);

            log.info("[{}] 解析完成，noteId={}", jobId, noteId);
        } catch (Exception e) {
            log.error("[{}] 解析失败: {}", jobId, e.getMessage(), e);
            updateJob(jobId, "FAILED", 0, null, e.getMessage());
        }
    }

    /**
     * 独立事务写入进度，避免外层事务未提交导致前端查不到更新
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateJob(String jobId, String status, int progress, String noteId, String errorMsg) {
        parseJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(status);
            job.setProgress(progress);
            job.setUpdatedAt(LocalDateTime.now());
            if (noteId != null) job.setNoteId(noteId);
            if (errorMsg != null) job.setErrorMessage(errorMsg);
            parseJobRepository.save(job);
        });
    }

    // ─── 查询状态 ──────────────────────────────────────────────────

    public ParseJobStatusDto getParseStatus(String jobId, Long userId) {
        ParseJob job = parseJobRepository.findByIdAndUserId(jobId, userId)
            .orElseThrow(() -> BusinessException.notFound("解析任务不存在"));

        return new ParseJobStatusDto(
            job.getId(),
            job.getStatus(),
            job.getProgress(),
            stageLabel(job.getStatus(), job.getProgress()),
            job.getFileName(),
            job.getNoteId(),
            job.getErrorMessage()
        );
    }

    /** 查询当前用户所有未完成的解析任务（页面刷新后恢复用） */
    public List<ParseJobStatusDto> getPendingJobs(Long userId) {
        return parseJobRepository
            .findByUserIdAndStatusIn(userId, List.of("PENDING", "PROCESSING"))
            .stream()
            .map(job -> new ParseJobStatusDto(
                job.getId(), job.getStatus(), job.getProgress(),
                stageLabel(job.getStatus(), job.getProgress()),
                job.getFileName(), job.getNoteId(), job.getErrorMessage()))
            .collect(Collectors.toList());
    }

    private String stageLabel(String status, int progress) {
        return switch (status) {
            case "PENDING" -> "等待解析...";
            case "DONE"    -> "解析完成！";
            case "FAILED"  -> "解析失败";
            default -> {
                if (progress < 25) yield "提取文档内容...";
                if (progress < 85) yield "AI 正在分析知识图谱...";
                yield "正在保存知识图谱...";
            }
        };
    }

    // ─── 流式问答 ──────────────────────────────────────────────────

    public SseEmitter chat(String noteId, String question, Long userId) {
        noteRepository.findById(noteId)
            .orElseThrow(() -> BusinessException.notFound("笔记不存在"));

        SseEmitter emitter = new SseEmitter(300_000L);

        executor.submit(() -> {
            try {
                String graphContext = buildGraphContext(noteId);
                String systemPrompt = String.format(CHAT_SYSTEM_TEMPLATE, graphContext);
                aiClient.streamChat(systemPrompt, question, emitter);
            } catch (Exception e) {
                log.error("AI 问答失败: {}", e.getMessage(), e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private String buildGraphContext(String noteId) {
        return graphNodeRepository.findByNoteId(noteId).stream()
            .map(n -> String.format("- %s（%s）：%s", n.getName(), n.getType(), n.getDescription()))
            .collect(Collectors.joining("\n"));
    }
}
