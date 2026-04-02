package com.knowledge.service;

import com.knowledge.dto.GraphData;
import com.knowledge.dto.GraphLinkDto;
import com.knowledge.dto.GraphNodeDto;
import com.knowledge.dto.NoteItem;
import com.knowledge.entity.GraphLink;
import com.knowledge.entity.GraphNode;
import com.knowledge.entity.Note;
import com.knowledge.exception.BusinessException;
import com.knowledge.repository.GraphLinkRepository;
import com.knowledge.repository.GraphNodeRepository;
import com.knowledge.repository.NoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;
    private final GraphNodeRepository graphNodeRepository;
    private final GraphLinkRepository graphLinkRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public List<NoteItem> listNotes(Long userId) {
        return noteRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(note -> {
                int nodeCount = graphNodeRepository.findByNoteId(note.getId()).size();
                return new NoteItem(
                    note.getId(),
                    note.getName(),
                    note.getCreatedAt().format(DATE_FMT),
                    nodeCount
                );
            })
            .toList();
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
        String noteId = java.util.UUID.randomUUID().toString();

        Note note = new Note();
        note.setId(noteId);
        note.setName(noteName);
        note.setUserId(userId);
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

        return new GraphData(nodes, links);
    }
}
