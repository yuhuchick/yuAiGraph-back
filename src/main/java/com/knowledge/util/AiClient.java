package com.knowledge.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.dto.GraphData;
import com.knowledge.dto.GraphLinkDto;
import com.knowledge.dto.GraphNodeDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiClient {

    @Qualifier("aiWebClient")
    private final WebClient aiWebClient;

    private final ObjectMapper objectMapper;

    @Value("${ai.api.chat-model}")
    private String chatModel;

    @Value("${ai.api.embedding-model}")
    private String embeddingModel;

    @Value("${ai.api.chunk-size:4000}")
    private int chunkSize;

    private static final String EXTRACT_PROMPT_TEMPLATE = """
        你是一个知识图谱专家，需要从以下文本中提取实体和关系。要求：
        1. 实体分为4类：概念（concept）、人物（person）、事件（event）、物体（object）
        2. 每个实体包含：id（如n1/n2...，每次从n1开始）、name、type、description（1-2句话）
        3. 关系格式：source（源实体id）、target（目标实体id）、relationship（如"包含"/"关联"/"因果"/"属于"）
        4. 必须输出合法 JSON，不要有任何额外内容或代码块标记
        
        输出格式：{"nodes":[{"id":"n1","name":"...","type":"concept","description":"..."}],"links":[{"source":"n1","target":"n2","relationship":"..."}]}
        
        文本内容：\
        """;

    /** 内容太短不值得提取的阈值（字符数） */
    private static final int MIN_CHUNK_LENGTH = 100;

    /**
     * 从文本中提取实体和关系，超长文本自动分块处理并合并结果
     */
    public GraphData extractGraphData(String text) {
        List<String> chunks = splitText(text).stream()
            .filter(c -> c.length() >= MIN_CHUNK_LENGTH)
            .toList();

        log.info("文本分块数量: {}（已过滤过短块）", chunks.size());

        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("文档内容不足，无法提取有效知识点");
        }

        List<GraphNodeDto> allNodes = new ArrayList<>();
        List<GraphLinkDto> allLinks = new ArrayList<>();
        int nodeIdOffset = 0;

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            log.info("正在处理第 {}/{} 块，长度: {} 字符", i + 1, chunks.size(), chunk.length());
            try {
                GraphData chunkResult = extractFromChunk(chunk, nodeIdOffset);
                allNodes.addAll(chunkResult.getNodes());
                allLinks.addAll(chunkResult.getLinks());
                nodeIdOffset += chunkResult.getNodes().size();
            } catch (WebClientResponseException e) {
                log.error("第 {}/{} 块 API 拒绝请求，状态码: {}，响应体: {}",
                    i + 1, chunks.size(), e.getStatusCode(), e.getResponseBodyAsString());
            } catch (Exception e) {
                log.error("第 {}/{} 块提取失败（跳过）: {}", i + 1, chunks.size(), e.getMessage(), e);
            }
        }

        deduplicateNodes(allNodes);
        return new GraphData(allNodes, allLinks);
    }

    /**
     * 流式 AI 问答，将结果逐字推送到 SseEmitter
     */
    public void streamChat(String systemPrompt, String question, SseEmitter emitter) {
        List<Map<String, String>> messages = List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", question)
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", chatModel);
        body.put("messages", messages);
        body.put("stream", true);
        body.put("temperature", 0.7);
        body.put("max_tokens", 4096);

        Flux<String> flux = aiWebClient.post()
            .uri("/chat/completions")
            .accept(MediaType.TEXT_EVENT_STREAM)   // 明确要求 SSE 流
            .bodyValue(body)
            .retrieve()
            .bodyToFlux(String.class);

        // publishOn(boundedElastic) 将 subscriber 切换到可阻塞的线程池，
        // 避免 emitter.send() 阻塞 Netty 事件循环导致流积压
        flux.publishOn(Schedulers.boundedElastic())
            .subscribe(
            raw -> {
                String data = raw.startsWith("data: ") ? raw.substring(6).trim() : raw.trim();
                if (data.isEmpty()) return;

                if ("[DONE]".equals(data)) {
                    try {
                        emitter.send(SseEmitter.event().data("[DONE]"));
                        emitter.complete();
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                    return;
                }
                try {
                    JsonNode node = objectMapper.readTree(data);
                    JsonNode delta = node.path("choices").path(0).path("delta");
                    String content = delta.path("content").asText(null);
                    if (content != null && !content.isEmpty()) {
                        emitter.send(SseEmitter.event().data(content));
                    }
                } catch (Exception e) {
                    log.debug("跳过无法解析的 SSE 行: {}", data);
                }
            },
            error -> {
                log.error("流式问答出错: {}", error.getMessage());
                emitter.completeWithError(error);
            },
            emitter::complete
        );
    }

    /**
     * 生成文本向量
     */
    public List<List<Double>> generateEmbeddings(List<String> texts) {
        Map<String, Object> body = Map.of(
            "model", embeddingModel,
            "input", texts
        );

        JsonNode response = aiWebClient.post()
            .uri("/embeddings")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();

        if (response == null) return Collections.emptyList();

        List<List<Double>> results = new ArrayList<>();
        response.path("data").forEach(item -> {
            List<Double> embedding = new ArrayList<>();
            item.path("embedding").forEach(v -> embedding.add(v.asDouble()));
            results.add(embedding);
        });
        return results;
    }

    // ─── private helpers ────────────────────────────────────────────────

    private GraphData extractFromChunk(String chunk, int nodeIdOffset) throws Exception {
        // 用拼接替代 String.format，避免 chunk 中含 % 字符时抛出 FormatException
        String prompt = EXTRACT_PROMPT_TEMPLATE + chunk;

        List<Map<String, String>> messages = List.of(
            Map.of("role", "user", "content", prompt)
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", chatModel);
        body.put("messages", messages);
        body.put("temperature", 0.1);
        // 强制模型只输出合法 JSON，避免多余文字或 markdown 代码块干扰解析
        body.put("response_format", Map.of("type", "json_object"));

        String rawJson = aiWebClient.post()
            .uri("/chat/completions")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(String.class)
            .block();

        // 兜底：去掉可能残留的思考块（<think>...</think>）
        String content = parseMessageContent(rawJson);
        content = stripThinkingBlock(content);
        String cleanJson = extractJson(content);

        GraphData result = objectMapper.readValue(cleanJson, GraphData.class);

        // 对节点 ID 加偏移量，避免多块合并时冲突
        if (nodeIdOffset > 0) {
            rewriteIds(result, nodeIdOffset);
        }

        return result;
    }

    private String parseMessageContent(String rawJson) throws Exception {
        JsonNode node = objectMapper.readTree(rawJson);
        return node.path("choices").path(0).path("message").path("content").asText();
    }

    /** 移除 Qwen3 思考模式输出的 <think>...</think> 块 */
    private String stripThinkingBlock(String text) {
        if (text == null) return "";
        return text.replaceAll("(?s)<think>.*?</think>", "").trim();
    }

    /** 从 AI 返回的文本中提取 JSON 部分（兼容 markdown 代码块） */
    private String extractJson(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("AI 返回内容为空");
        }
        // 去除 markdown 代码块标记
        Pattern pattern = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        // 直接找第一个 { 到最后一个 }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text.trim();
    }

    private void rewriteIds(GraphData data, int offset) {
        Map<String, String> idMap = new HashMap<>();
        for (GraphNodeDto node : data.getNodes()) {
            String oldId = node.getId();
            String newId = "n" + (extractNumber(oldId) + offset);
            idMap.put(oldId, newId);
            node.setId(newId);
        }
        for (GraphLinkDto link : data.getLinks()) {
            link.setSource(idMap.getOrDefault(link.getSource(), link.getSource()));
            link.setTarget(idMap.getOrDefault(link.getTarget(), link.getTarget()));
        }
    }

    private int extractNumber(String id) {
        try {
            return Integer.parseInt(id.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 按字符数分块，保留段落完整性 */
    private List<String> splitText(String text) {
        if (text.length() <= chunkSize) {
            return new ArrayList<>(List.of(text));
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            // 尽量在段落边界截断，但保证至少推进 chunkSize/2 避免死循环
            if (end < text.length()) {
                int lastNewline = text.lastIndexOf('\n', end);
                if (lastNewline > start + chunkSize / 2) {
                    end = lastNewline;
                }
            }
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            // 确保 start 始终向前推进，防止死循环
            start = Math.max(end, start + 1);
        }
        return chunks;
    }

    /** 按 name 去重，保留最先出现的节点 */
    private void deduplicateNodes(List<GraphNodeDto> nodes) {
        Set<String> seen = new LinkedHashSet<>();
        nodes.removeIf(n -> !seen.add(n.getName().toLowerCase()));
    }
}
