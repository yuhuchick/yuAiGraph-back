package com.knowledge.controller;

import com.knowledge.common.Result;
import com.knowledge.dto.GraphData;
import com.knowledge.dto.NoteItem;
import com.knowledge.service.NoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @GetMapping("/notes")
    public ResponseEntity<Result<List<NoteItem>>> listNotes(
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.valueOf(userDetails.getUsername());
        return ResponseEntity.ok(Result.ok(noteService.listNotes(userId)));
    }

    @DeleteMapping("/notes/{noteId}")
    public ResponseEntity<Result<Void>> deleteNote(
        @PathVariable String noteId,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.valueOf(userDetails.getUsername());
        noteService.deleteNote(noteId, userId);
        return ResponseEntity.ok(Result.ok());
    }

    @GetMapping("/graph")
    public ResponseEntity<Result<GraphData>> getGraph(
        @RequestParam String noteId,
        @AuthenticationPrincipal UserDetails userDetails
    ) {
        Long userId = Long.valueOf(userDetails.getUsername());
        return ResponseEntity.ok(Result.ok(noteService.getGraph(noteId, userId)));
    }
}
