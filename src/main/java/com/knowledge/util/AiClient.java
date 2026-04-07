package com.knowledge.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.dto.GraphData;
import com.knowledge.dto.GraphLinkDto;
import com.knowledge.dto.GraphNodeDto;
import com.knowledge.dto.InsightChartSpecDto;
import com.knowledge.dto.InsightSeriesDto;
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
        你是一个知识图谱与数据可视化专家，需要从以下文本中同时提取：(A) 实体关系图谱，(B) 文档中可结构化展示的重点（多张小图，勿把所有信息塞进一张图）。

        【图谱 nodes / links】
        1. 实体分为4类：概念（concept）、人物（person）、事件（event）、物体（object）
        2. 每个实体：id（本块内从 n1、n2…连续编号）、name、type、description（1-2句话）
        2a. nodes、links、insightCharts 均为数组；数组中每一项必须是完整的 JSON 对象（含花括号与键值对），禁止出现纯字符串元素（如 "id"、"name"）或残缺片段，否则无法解析。
        3. 关系：source、target（均为实体 id）、relationship（如「包含」「关联」「因果」「属于」等简短动词短语）
        4. 图谱与图表中的数值必须能在原文中找到依据；禁止编造与原文无关的数据。

        【语义图表 insightCharts】
        5. 根据段落内容选择不同 chartType，使「一类信息对应一种合适的图」：
           - 占比、构成、份额 → pie
           - 类别数量对比、排名 → bar
           - 时间序列、阶段变化、趋势 → line
           - 多维度能力/特征对比 → radar
           - 两变量关系、分布点 → scatter（用 scatterPoints，不用 categories+series）
        6. 每一项含：id（本块内唯一，如 c1、c2）、title、rationale（1句：为何选该图、对应文中哪类信息）、chartType（仅允许 pie|bar|line|radar|scatter）、categories（类目名数组，与数值一一对应；scatter 可省略或为空数组）、series（数组，每项 name + data 为与 categories 等长的数值数组；可多系列用于分组柱状/多折线）、scatter 时 scatterPoints 为 [[x,y],...]。
        7. 若本段没有可量化的对比/趋势/占比等，insightCharts 输出空数组 []。不要为了凑数重复建图。

        输出唯一一个 JSON 对象，键为 nodes、links、insightCharts，不要 markdown 或任何额外文字。

        输出格式示例（字段说明用，勿照抄数值）：
        {"nodes":[...],"links":[...],"insightCharts":[{"id":"c1","title":"...","rationale":"...","chartType":"bar","categories":["A","B"],"series":[{"name":"数量","data":[1,2]}]}]}

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
        List<InsightChartSpecDto> allInsightCharts = new ArrayList<>();
        int nodeIdOffset = 0;

        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i);
            log.info("正在处理第 {}/{} 块，长度: {} 字符", i + 1, chunks.size(), chunk.length());
            try {
                GraphData chunkResult = extractFromChunk(chunk, nodeIdOffset);
                allNodes.addAll(chunkResult.getNodes());
                allLinks.addAll(chunkResult.getLinks());
                if (chunkResult.getInsightCharts() != null) {
                    allInsightCharts.addAll(chunkResult.getInsightCharts());
                }
                nodeIdOffset += chunkResult.getNodes().size();
            } catch (WebClientResponseException e) {
                log.error("第 {}/{} 块 API 拒绝请求，状态码: {}，响应体: {}",
                    i + 1, chunks.size(), e.getStatusCode(), e.getResponseBodyAsString());
            } catch (Exception e) {
                log.error("第 {}/{} 块提取失败（跳过）: {}", i + 1, chunks.size(), e.getMessage(), e);
            }
        }

        deduplicateNodes(allNodes);
        assignUniqueInsightChartIds(allInsightCharts);
        return new GraphData(allNodes, allLinks, allInsightCharts);
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

        GraphData result = parseGraphDataLenient(cleanJson);

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

    /**
     * 容错解析模型 JSON：跳过 nodes/links/insightCharts 中非对象元素（模型偶发输出 "id" 等字符串会导致直接反序列化失败）。
     */
    private GraphData parseGraphDataLenient(String cleanJson) throws Exception {
        JsonNode root = objectMapper.readTree(cleanJson);
        if (!root.isObject()) {
            throw new IllegalStateException("AI 返回内容不是 JSON 对象");
        }

        List<GraphNodeDto> nodes = new ArrayList<>();
        JsonNode nodesArr = root.path("nodes");
        if (nodesArr.isArray()) {
            int i = 0;
            for (JsonNode el : nodesArr) {
                if (!el.isObject()) {
                    log.warn("跳过非法 nodes[{}]（须为对象，实际为 {}）", i, el.getNodeType());
                    i++;
                    continue;
                }
                String id = textField(el, "id");
                String name = textField(el, "name");
                if (id.isEmpty() || name.isEmpty()) {
                    log.warn("跳过缺少 id 或 name 的 nodes[{}]", i);
                    i++;
                    continue;
                }
                String type = textField(el, "type");
                if (type.isEmpty()) {
                    type = "concept";
                }
                nodes.add(new GraphNodeDto(id, name, type, textField(el, "description")));
                i++;
            }
        }

        List<GraphLinkDto> links = new ArrayList<>();
        JsonNode linksArr = root.path("links");
        if (linksArr.isArray()) {
            int i = 0;
            for (JsonNode el : linksArr) {
                if (!el.isObject()) {
                    log.warn("跳过非法 links[{}]（须为对象）", i);
                    i++;
                    continue;
                }
                String source = textField(el, "source");
                String target = textField(el, "target");
                if (source.isEmpty() || target.isEmpty()) {
                    i++;
                    continue;
                }
                links.add(new GraphLinkDto(source, target, textField(el, "relationship")));
                i++;
            }
        }

        List<InsightChartSpecDto> insightCharts = new ArrayList<>();
        JsonNode chartsArr = root.path("insightCharts");
        if (chartsArr.isArray()) {
            int i = 0;
            for (JsonNode el : chartsArr) {
                if (!el.isObject()) {
                    log.warn("跳过非法 insightCharts[{}]（须为对象）", i);
                    i++;
                    continue;
                }
                InsightChartSpecDto chart = parseInsightChartObject(el);
                if (chart != null) {
                    insightCharts.add(chart);
                }
                i++;
            }
        }

        return new GraphData(nodes, links, insightCharts);
    }

    private static String textField(JsonNode obj, String key) {
        JsonNode v = obj.get(key);
        if (v == null || v.isNull()) {
            return "";
        }
        if (v.isTextual()) {
            return v.asText().trim();
        }
        if (v.isNumber() || v.isBoolean()) {
            return v.asText();
        }
        return "";
    }

    private InsightChartSpecDto parseInsightChartObject(JsonNode el) {
        String id = textField(el, "id");
        String title = textField(el, "title");
        if (id.isEmpty() || title.isEmpty()) {
            return null;
        }
        return new InsightChartSpecDto(
            id,
            title,
            textField(el, "rationale"),
            textField(el, "chartType"),
            parseStringList(el.get("categories")),
            parseSeriesList(el.get("series")),
            parseScatterPoints(el.get("scatterPoints"))
        );
    }

    private List<String> parseStringList(JsonNode arr) {
        if (arr == null || !arr.isArray()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode x : arr) {
            if (x.isTextual()) {
                out.add(x.asText());
            } else if (x.isNumber()) {
                out.add(x.asText());
            }
        }
        return out;
    }

    private List<InsightSeriesDto> parseSeriesList(JsonNode arr) {
        if (arr == null || !arr.isArray()) {
            return Collections.emptyList();
        }
        List<InsightSeriesDto> out = new ArrayList<>();
        for (JsonNode o : arr) {
            if (!o.isObject()) {
                continue;
            }
            String name = textField(o, "name");
            List<Double> data = parseDoubleList(o.get("data"));
            if (name.isEmpty() || data.isEmpty()) {
                continue;
            }
            out.add(new InsightSeriesDto(name, data));
        }
        return out;
    }

    private List<Double> parseDoubleList(JsonNode arr) {
        if (arr == null || !arr.isArray()) {
            return Collections.emptyList();
        }
        List<Double> out = new ArrayList<>();
        for (JsonNode x : arr) {
            if (x.isNumber()) {
                out.add(x.asDouble());
            } else if (x.isTextual()) {
                try {
                    out.add(Double.parseDouble(x.asText().trim()));
                } catch (NumberFormatException ignored) {
                    // skip
                }
            }
        }
        return out;
    }

    private List<List<Double>> parseScatterPoints(JsonNode arr) {
        if (arr == null || !arr.isArray()) {
            return Collections.emptyList();
        }
        List<List<Double>> out = new ArrayList<>();
        for (JsonNode row : arr) {
            if (!row.isArray() || row.size() < 2) {
                continue;
            }
            Double x = readDoubleCell(row.get(0));
            Double y = readDoubleCell(row.get(1));
            if (x != null && y != null) {
                List<Double> pair = new ArrayList<>(2);
                pair.add(x);
                pair.add(y);
                out.add(pair);
            }
        }
        return out;
    }

    private static Double readDoubleCell(JsonNode x) {
        if (x == null || x.isNull()) {
            return null;
        }
        if (x.isNumber()) {
            return x.asDouble();
        }
        if (x.isTextual()) {
            try {
                return Double.parseDouble(x.asText().trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
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

    /** 多块合并时保证 insightCharts 的 id 全局唯一 */
    private void assignUniqueInsightChartIds(List<InsightChartSpecDto> charts) {
        Set<String> used = new HashSet<>();
        int seq = 1;
        for (InsightChartSpecDto c : charts) {
            String id = c.getId();
            if (id == null || id.isBlank() || used.contains(id)) {
                String candidate;
                do {
                    candidate = "chart_" + seq++;
                } while (used.contains(candidate));
                c.setId(candidate);
                id = candidate;
            }
            used.add(id);
        }
    }
}
