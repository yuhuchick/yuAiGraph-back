package com.knowledge.service;

import com.knowledge.dto.GraphData;
import com.knowledge.dto.ShareResponse;
import com.knowledge.dto.SharedGraphResponse;
import com.knowledge.entity.Note;
import com.knowledge.entity.ShareCode;
import com.knowledge.exception.BusinessException;
import com.knowledge.repository.NoteRepository;
import com.knowledge.repository.ShareCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShareService {

    private final ShareCodeRepository shareCodeRepository;
    private final NoteRepository noteRepository;
    private final NoteService noteService;

    @Value("${app.base-url:http://localhost:3000}")
    private String appBaseUrl;

    public ShareResponse createShare(String noteId, String permission, Long userId) {
        Note note = noteRepository.findById(noteId)
            .orElseThrow(() -> BusinessException.notFound("笔记不存在"));

        if (!note.getUserId().equals(userId)) {
            throw BusinessException.forbidden("无权分享此笔记");
        }

        String code = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        ShareCode shareCode = new ShareCode();
        shareCode.setCode(code);
        shareCode.setNoteId(noteId);
        shareCode.setPermission(permission);
        shareCodeRepository.save(shareCode);

        String shareUrl = appBaseUrl + "/share/" + code;
        return new ShareResponse(code, shareUrl, permission);
    }

    public SharedGraphResponse getSharedGraph(String code) {
        ShareCode shareCode = shareCodeRepository.findById(code)
            .orElseThrow(() -> BusinessException.notFound("分享链接不存在或已失效"));

        Note note = noteRepository.findById(shareCode.getNoteId())
            .orElseThrow(() -> BusinessException.notFound("关联笔记不存在"));

        GraphData graph = noteService.getGraphByShareCode(shareCode.getNoteId());

        return new SharedGraphResponse(note.getName(), shareCode.getPermission(), graph);
    }
}
