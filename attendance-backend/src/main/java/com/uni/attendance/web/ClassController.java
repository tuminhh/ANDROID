package com.uni.attendance.web;

import com.uni.attendance.security.AuthUser;
import com.uni.attendance.service.ClassService;
import com.uni.attendance.web.Dtos.AddStudentsRequest;
import com.uni.attendance.web.Dtos.AddStudentsResult;
import com.uni.attendance.web.Dtos.BatchClassImportItem;
import com.uni.attendance.web.Dtos.BatchClassImportResult;
import com.uni.attendance.web.Dtos.ClassDetail;
import com.uni.attendance.web.Dtos.ClassReport;
import com.uni.attendance.web.Dtos.ClassView;
import com.uni.attendance.web.Dtos.CreateClassRequest;
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
@RequestMapping("/api/classes")
public class ClassController {
    private final ClassService classes;

    public ClassController(ClassService classes) {
        this.classes = classes;
    }

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ClassView create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody CreateClassRequest request) {
        return classes.create(user.userId(), request);
    }

    /** Nhập hàng loạt lớp học phần kèm lịch học TKB bằng JSON (chỉ dành cho giáo viên). */
    @PostMapping(value = {"/import-json", "/import-json/"})
    @PreAuthorize("hasRole('TEACHER')")
    public BatchClassImportResult importJson(
            @AuthenticationPrincipal AuthUser user,
            @RequestBody List<BatchClassImportItem> items) {
        return classes.importBatchClasses(user.userId(), items);
    }

    /** Các lớp do giảng viên phụ trách. */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('TEACHER')")
    public List<ClassView> mine(@AuthenticationPrincipal AuthUser user) {
        return classes.mine(user.userId());
    }

    /** Các lớp mà sinh viên có tên trong danh sách. */
    @GetMapping("/enrolled")
    @PreAuthorize("hasRole('STUDENT')")
    public List<ClassView> enrolled(@AuthenticationPrincipal AuthUser user) {
        return classes.enrolledFor(user.userId());
    }

    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasRole('TEACHER')")
    public ClassDetail detail(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return classes.detail(user.userId(), id);
    }

    /** Thêm hàng loạt sinh viên vào danh sách lớp theo mã sinh viên. */
    @PostMapping("/{id:\\d+}/students")
    @PreAuthorize("hasRole('TEACHER')")
    public AddStudentsResult addStudents(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                         @Valid @RequestBody AddStudentsRequest request) {
        return classes.addStudents(user.userId(), id, request);
    }

    @DeleteMapping("/{id:\\d+}/students/{studentId}")
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeStudent(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                              @PathVariable String studentId) {
        classes.removeStudent(user.userId(), id, studentId);
    }

    /** Xóa lớp học phần (chỉ giảng viên phụ trách). */
    @DeleteMapping(value = {"/{id}", "/{id}/", "/{id:\\d+}", "/{id:\\d+}/"})
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteClass(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        classes.deleteClass(user.userId(), id);
    }

    /** Hỗ trợ thêm phương thức POST để xóa lớp phòng trường hợp môi trường mạng chặn DELETE. */
    @PostMapping(value = {"/{id}/delete", "/{id}/delete/"})
    @PreAuthorize("hasRole('TEACHER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteClassPost(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        classes.deleteClass(user.userId(), id);
    }

    /** Tỷ lệ chuyên cần của từng sinh viên trong lớp. */
    @GetMapping("/{id:\\d+}/report")
    @PreAuthorize("hasRole('TEACHER')")
    public ClassReport report(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return classes.report(user.userId(), id);
    }

    /** Bảng điểm danh cả lớp dạng CSV (UTF-8 có BOM để Excel hiển thị đúng tiếng Việt). */
    @GetMapping("/{id:\\d+}/export")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        String csv = "﻿" + classes.exportCsv(user.userId(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"class-" + id + ".csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.getBytes(StandardCharsets.UTF_8));
    }

    /** Bảng điểm danh cả lớp dạng Excel (.xlsx) có định dạng. */
    @GetMapping("/{id:\\d+}/export.xlsx")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<byte[]> exportXlsx(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"class-" + id + ".xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(classes.exportXlsx(user.userId(), id));
    }
}
