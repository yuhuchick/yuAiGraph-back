package com.knowledge.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowledge.dto.GraphData;
import com.knowledge.dto.GraphLinkDto;
import com.knowledge.dto.GraphNodeDto;
import com.knowledge.dto.InsightChartSpecDto;
import com.knowledge.dto.NoteItem;
import com.knowledge.entity.GraphLink;
import com.knowledge.entity.GraphNode;
import com.knowledge.entity.Note;
import com.knowledge.exception.BusinessException;
import com.knowledge.dto.NoteListResponse;
import com.knowledge.repository.GraphLinkRepository;
import com.knowledge.repository.GraphNodeRepository;
import com.knowledge.repository.NoteRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NoteService {

    private static final TypeReference<List<InsightChartSpecDto>> INSIGHT_LIST_TYPE =
        new TypeReference<>() {};

    private final NoteRepository noteRepository;
    private final ObjectMapper objectMapper;
    private final GraphNodeRepository graphNodeRepository;
    private final GraphLinkRepository graphLinkRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public NoteListResponse listNotesPage(Long userId, int page, int size, String category, String keyword) {
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        var pageable = PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<Note> spec = (root, query, cb) -> {
            Predicate pred = cb.equal(root.get("userId"), userId);
            if (StringUtils.hasText(category) && !"__all__".equals(category)) {
                if ("__none__".equals(category)) {
                    pred = cb.and(pred, cb.or(
                        cb.isNull(root.get("category")),
                        cb.equal(root.get("category"), "")
                    ));
                } else {
                    pred = cb.and(pred, cb.equal(root.get("category"), category.trim()));
                }
            }
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                pred = cb.and(pred, cb.like(cb.lower(root.get("name")), like));
            }
            return pred;
        };

        Page<Note> result = noteRepository.findAll(spec, pageable);
        List<NoteItem> items = result.getContent().stream()
            .map(this::toNoteItem)
            .toList();

        LocalDateTime monthStart = LocalDateTime.now()
            .withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);

        return NoteListResponse.builder()
            .items(items)
            .total(result.getTotalElements())
            .page(p)
            .size(s)
            .totalPages(result.getTotalPages())
            .allNotesCount(noteRepository.countByUserId(userId))
            .totalNodeCount(graphNodeRepository.countNodesForUser(userId))
            .notesThisMonth(noteRepository.countByUserIdAndCreatedAtGreaterThanEqual(userId, monthStart))
            .build();
    }

    public List<String> listDistinctCategories(Long userId) {
        return noteRepository.findDistinctCategoriesByUserId(userId);
    }

    private NoteItem toNoteItem(Note note) {
        int nodeCount = graphNodeRepository.findByNoteId(note.getId()).size();
        String cat = note.getCategory() != null ? note.getCategory() : "";
        return new NoteItem(
            note.getId(),
            note.getName(),
            note.getCreatedAt().format(DATE_FMT),
            nodeCount,
            cat
        );
    }

    private static String normalizeCategory(String c) {
        if (c == null) {
            return "";
        }
        String t = c.trim();
        if (t.isEmpty()) {
            return "";
        }
        return t.length() > 64 ? t.substring(0, 64) : t;
    }

    @Transactional
    public void deleteNote(String noteId, Long userId) {
        Note note = noteRepository.findById(noteId)
            .orElseThrow(() -> BusinessException.notFound("笔记不存在"));

        if (!note.getUserId().equals(userId)) {
            throw BusinessException.forbidden("无权删除此笔记");
        }

        graphLinkRepository.deleteByNoteId(noteId);
        graphNodeRepository.deleteByNoteId(noteId);
        noteRepository.delete(note);
    }

    public GraphData getGraph(String noteId, Long userId) {
        Note note = noteRepository.findById(noteId)
            .orElseThrow(() -> BusinessException.notFound("未找到对应图谱"));

        if (!note.getUserId().equals(userId)) {
            throw BusinessException.forbidden("无权查看此笔记");
        }

        return buildGraphData(noteId);
    }

    public GraphData getGraphByShareCode(String noteId) {
        return buildGraphData(noteId);
    }

    @Transactional
    public String saveGraph(String noteName, Long userId, GraphData graphData) {
        return saveGraph(noteName, userId, graphData, "");
    }

    @Transactional
    public String saveGraph(String noteName, Long userId, GraphData graphData, String category) {
        String noteId = java.util.UUID.randomUUID().toString();

        Note note = new Note();
        note.setId(noteId);
        note.setName(noteName);
        note.setUserId(userId);
        note.setCategory(normalizeCategory(category));
        if (graphData.getInsightCharts() != null && !graphData.getInsightCharts().isEmpty()) {
            try {
                note.setInsightsJson(objectMapper.writeValueAsString(graphData.getInsightCharts()));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("序列化 insightCharts 失败", e);
            }
        }
        noteRepository.save(note);

        List<GraphNode> nodes = graphData.getNodes().stream().map(dto -> {
            GraphNode node = new GraphNode();
            node.setNoteId(noteId);
            node.setNodeId(dto.getId());
            node.setName(dto.getName());
            node.setType(dto.getType());
            node.setDescription(dto.getDescription());
            return node;
        }).toList();
        graphNodeRepository.saveAll(nodes);

        List<GraphLink> links = graphData.getLinks().stream().map(dto -> {
            GraphLink link = new GraphLink();
            link.setNoteId(noteId);
            link.setSource(dto.getSource());
            link.setTarget(dto.getTarget());
            link.setRelationship(dto.getRelationship());
            return link;
        }).toList();
        graphLinkRepository.saveAll(links);

        return noteId;
    }

    private GraphData buildGraphData(String noteId) {
        List<GraphNodeDto> nodes = graphNodeRepository.findByNoteId(noteId).stream()
            .map(n -> new GraphNodeDto(n.getNodeId(), n.getName(), n.getType(), n.getDescription()))
            .toList();

        List<GraphLinkDto> links = graphLinkRepository.findByNoteId(noteId).stream()
            .map(l -> new GraphLinkDto(l.getSource(), l.getTarget(), l.getRelationship()))
            .toList();

        List<InsightChartSpecDto> insights = parseInsights(noteRepository.findById(noteId)
            .map(Note::getInsightsJson)
            .orElse(null));

        return new GraphData(nodes, links, insights);
    }

    private List<InsightChartSpecDto> parseInsights(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<InsightChartSpecDto> list = objectMapper.readValue(json, INSIGHT_LIST_TYPE);
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
