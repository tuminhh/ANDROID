package com.uni.attendance.web;

import com.uni.attendance.security.AuthUser;
import com.uni.attendance.service.SessionService;
import com.uni.attendance.web.Dtos.ManualMarkRequest;
import com.uni.attendance.web.Dtos.OpenSessionRequest;
import com.uni.attendance.web.Dtos.RecordView;
import com.uni.attendance.web.Dtos.SessionRecords;
import com.uni.attendance.web.Dtos.SessionView;
import com.uni.attendance.web.Dtos.TeacherSessionView;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {
    private final SessionService sessions;

    public SessionController(SessionService sessions) {
        this.sessions = sessions;
    }

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.CREATED)
    public TeacherSessionView open(@AuthenticationPrincipal AuthUser user,
                                   @Valid @RequestBody OpenSessionRequest request) {
        return sessions.open(user.userId(), request);
    }

    /** Các buổi đang mở của những lớp học phần mà sinh viên có tên trong danh sách. */
    @GetMapping("/active")
    @PreAuthorize("hasRole('STUDENT')")
    public List<SessionView> active(@AuthenticationPrincipal AuthUser user) {
        return sessions.listActive(user.userId());
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('TEACHER')")
    public List<TeacherSessionView> mine(@AuthenticationPrincipal AuthUser user) {
        return sessions.mine(user.userId());
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('TEACHER')")
    public TeacherSessionView close(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return sessions.close(user.userId(), id);
    }

    @GetMapping("/{id}/records")
    @PreAuthorize("hasRole('TEACHER')")
    public SessionRecords records(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return sessions.recordsOf(user.userId(), id);
    }

    /** Xuất CSV (UTF-8 có BOM để Excel hiển thị đúng tiếng Việt). */
    @GetMapping("/{id}/export")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        String csv = "﻿" + sessions.exportCsv(user.userId(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"attendance-" + id + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    /** Xuất Excel (.xlsx) có định dạng: có mặt tô xanh, vắng tô đỏ. */
    @GetMapping("/{id}/export.xlsx")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<byte[]> exportXlsx(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"attendance-" + id + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(sessions.exportXlsx(user.userId(), id));
    }

    @PostMapping("/{id}/records")
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.CREATED)
    public RecordView markManually(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                   @Valid @RequestBody ManualMarkRequest request) {
        return sessions.markManually(user.userId(), id, request.studentId());
    }

    @DeleteMapping("/{id}/records/{studentId}")
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMark(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                           @PathVariable String studentId) {
        sessions.deleteMark(user.userId(), id, studentId);
    }
}
