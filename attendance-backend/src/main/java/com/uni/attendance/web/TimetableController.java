package com.uni.attendance.web;

import com.uni.attendance.security.AuthUser;
import com.uni.attendance.service.GeminiService;
import com.uni.attendance.service.TimetableService;
import com.uni.attendance.web.Dtos.GeminiScanRequest;
import com.uni.attendance.web.Dtos.GeminiScanResponse;
import com.uni.attendance.web.Dtos.TimetableItemDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API quản lý Thời khóa biểu & Tích hợp Gemini AI Vision trích xuất từ ảnh chụp màn hình.
 */
@RestController
@RequestMapping("/api/timetable")
public class TimetableController {

    private final TimetableService timetableService;
    private final GeminiService geminiService;

    public TimetableController(TimetableService timetableService, GeminiService geminiService) {
        this.timetableService = timetableService;
        this.geminiService = geminiService;
    }

    /**
     * Lấy danh sách thời khóa biểu của người dùng hiện tại.
     */
    @GetMapping("/my")
    public List<TimetableItemDto> getMyTimetable(@AuthenticationPrincipal AuthUser user) {
        return timetableService.getMyTimetable(user.userId());
    }

    /**
     * Nhập hoặc cập nhật thời khóa biểu bằng mảng JSON.
     */
    @PostMapping("/import-json")
    @ResponseStatus(HttpStatus.OK)
    public List<TimetableItemDto> importJson(
            @AuthenticationPrincipal AuthUser user,
            @RequestBody List<TimetableItemDto> items) {
        return timetableService.saveUserTimetable(user.userId(), items);
    }

    /**
     * Quét ảnh chụp màn hình thời khóa biểu bằng Gemini 3.8 Flash và tự động lưu.
     */
    @PostMapping("/scan-gemini")
    public GeminiScanResponse scanWithGemini(
            @AuthenticationPrincipal AuthUser user,
            @RequestBody GeminiScanRequest request) {
        List<TimetableItemDto> extracted = geminiService.parseTimetableFromImage(request.imageBase64());
        if (extracted != null && !extracted.isEmpty()) {
            timetableService.saveUserTimetable(user.userId(), extracted);
            return new GeminiScanResponse(extracted, "Đã trích xuất thành công " + extracted.size() + " môn học bằng Gemini AI!");
        }
        return new GeminiScanResponse(List.of(), "Không tìm thấy thông tin môn học nào trong ảnh.");
    }

    /**
     * Xóa toàn bộ thời khóa biểu của người dùng hiện tại.
     */
    @DeleteMapping("/my")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMyTimetable(@AuthenticationPrincipal AuthUser user) {
        timetableService.deleteMyTimetable(user.userId());
    }
}
