package com.knowledge.service;

import com.knowledge.dto.AdminPromptConfigDto;
import com.knowledge.entity.PromptConfig;
import com.knowledge.exception.BusinessException;
import com.knowledge.repository.PromptConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PromptConfigService {

    public static final String KEY_CHAT_SYSTEM = "chat_system_template";
    public static final String KEY_GRAPH_EXTRACT = "graph_extract_template";

    public static final String CHAT_SYSTEM_DEFAULT = """
        你是一个知识图谱问答助手。以下是用户的知识图谱内容：
        
        %s
        
        请根据以上图谱中的实体和关系回答用户的问题。
        回答要简洁清晰，优先引用图谱中的实体和关系，如有必要可适当补充相关知识。""";

    public static final String GRAPH_EXTRACT_DEFAULT = """
        你是一个知识图谱与数据可视化专家，需要从以下文本中同时提取：(A) 实体关系图谱，(B) 文档中可结构化展示的重点（多张小图，勿把所有信息塞进一张图）。

        【图谱 nodes / links】
        1. 实体分为4类：概念（concept）、人物（person）、事件（event）、物体（object）
        2. 每个实体：id（本块内从 n1、n2…连续编号）、name、type、description（1-2句话）
        2a. nodes、links、insightCharts 均为数组；数组中每一项必须是完整的 JSON 对象（含花括号与键值对），禁止出现纯字符串元素（如 "id"、"name"）或残缺片段，否则无法解析。
        3. 关系：source、target（均为实体 id）、relationship（如「包含」「关联」「因果」「属于」等简短动词短语）
        4. 图谱与图表中的数值必须能在原文中找到依据；禁止编造与原文无关的数据。

        【语义图表 insightCharts】
        5. 优先产出能反映文档论点、阶段对比、时间线、TOP 清单、术语与人物要点等「有叙事价值」的图或表；禁止仅为「节点类型占比、关系标签出现次数、节点连接度」等纯图谱结构统计单独建图（系统会以表格展示实体与关系清单）。
        6. chartType 取值：
           - pie：文中明确的占比、构成、份额
           - bar：类别数量对比、排名、多组对比
           - line：时间序列、阶段变化、趋势
           - radar：多维度特征/能力对比
           - scatter：两变量关系，使用 scatterPoints 为 [[x,y],...]，不用 categories+series 承载主数据
           - table：术语定义表、阶段对照、人物/事件要点罗列、论点与论据清单等；必须提供 tableColumns（表头字符串数组）与 tableRows（二维数组，每行长度与表头列数一致）；table 时可省略或留空 categories、series、scatterPoints
        7. 每一项含：id（本块内唯一，如 c1）、title、rationale（1句说明对应文中哪类信息）、chartType 及对应数据字段。
        8. 若本段没有值得单独成图/表的内容，insightCharts 输出 []；禁止为用图谱结构统计凑数。

        输出唯一一个 JSON 对象，键为 nodes、links、insightCharts，不要 markdown 或任何额外文字。

        输出格式示例（字段说明用，勿照抄数值）：
        {"nodes":[...],"links":[...],"insightCharts":[{"id":"c1","title":"...","rationale":"...","chartType":"bar","categories":["A","B"],"series":[{"name":"数量","data":[1,2]}]},{"id":"c2","title":"术语表","rationale":"...","chartType":"table","tableColumns":["术语","含义"],"tableRows":[["A","定义A"],["B","定义B"]]}]}

        文本内容：\
        """;

    private final PromptConfigRepository promptConfigRepository;

    private Map<String, String> defaults() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(KEY_CHAT_SYSTEM, CHAT_SYSTEM_DEFAULT);
        map.put(KEY_GRAPH_EXTRACT, GRAPH_EXTRACT_DEFAULT);
        return map;
    }

    public String getPrompt(String key) {
        String def = defaults().get(key);
        if (def == null) {
            throw BusinessException.notFound("未知提示词键: " + key);
        }
        return promptConfigRepository.findById(key)
            .map(PromptConfig::getContent)
            .filter(x -> x != null && !x.isBlank())
            .orElse(def);
    }

    public List<AdminPromptConfigDto> listConfigs() {
        Map<String, String> defs = defaults();
        return defs.entrySet().stream()
            .map(e -> {
                String key = e.getKey();
                String content = promptConfigRepository.findById(key)
                    .map(PromptConfig::getContent)
                    .filter(x -> x != null && !x.isBlank())
                    .orElse(e.getValue());
                String label = KEY_CHAT_SYSTEM.equals(key) ? "问答系统提示词" : "图谱抽取提示词";
                return new AdminPromptConfigDto(key, label, content, e.getValue());
            })
            .toList();
    }

    @Transactional
    public AdminPromptConfigDto updateConfig(String key, String content) {
        Map<String, String> defs = defaults();
        String def = defs.get(key);
        if (def == null) {
            throw BusinessException.notFound("未知提示词键: " + key);
        }
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank()) {
            throw BusinessException.badRequest("提示词内容不能为空");
        }

        PromptConfig cfg = promptConfigRepository.findById(key).orElseGet(PromptConfig::new);
        cfg.setConfigKey(key);
        cfg.setContent(normalized);
        promptConfigRepository.save(cfg);

        String label = KEY_CHAT_SYSTEM.equals(key) ? "问答系统提示词" : "图谱抽取提示词";
        return new AdminPromptConfigDto(key, label, normalized, def);
    }
}
