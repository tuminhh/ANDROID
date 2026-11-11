package com.uni.attendance.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.uni.attendance.domain.Role;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Các đối tượng vào/ra của API. */
public final class Dtos {
    private Dtos() {}

    // ---------- Xác thực ----------
    public record RegisterRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{3,32}$",
                    message = "Mã chỉ gồm chữ, số, dấu . _ - và dài 3-32 ký tự") String userId,
            @NotBlank @Size(min = 2, max = 100) String name,
            @NotBlank @Size(min = 8, max = 72, message = "Mật khẩu dài 8-72 ký tự") String password,
            @NotNull Role role,
            String inviteCode) {}

    public record LoginRequest(@NotBlank String userId, @NotBlank String password) {}

    public record UserDto(String userId, String name, Role role, boolean faceEnrolled) {}

    public record AuthResponse(String token, UserDto user) {}

    // ---------- Khuôn mặt ----------
    /** password chỉ bắt buộc khi đăng ký lại khuôn mặt (đã có mẫu trước đó). */
    public record FaceEnrollRequest(@NotNull @Size(min = 16, max = 2048) float[] embedding, String password) {}

    public record FaceStatus(boolean enrolled) {}

    // ---------- Lớp học phần ----------
    public record CreateClassRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{2,32}$",
                    message = "Mã lớp chỉ gồm chữ, số, dấu . _ - và dài 2-32 ký tự") String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 50) String semester,
            Integer dayOfWeek,
            String shift,
            String room) {
        public CreateClassRequest(String code, String name, String semester) {
            this(code, name, semester, null, null, null);
        }
    }

    public record BatchClassImportItem(
            @NotBlank String code,
            @NotBlank String name,
            String semester,
            Integer dayOfWeek,
            String shift,
            String room,
            List<String> studentIds) {}

    public record BatchClassImportResult(
            int createdCount,
            int updatedCount,
            int totalStudentsEnrolled,
            List<String> messages) {}

    public record ClassView(Long id, String code, String name, String semester,
                            Integer dayOfWeek, String shift, String room,
                            String teacherName, long studentCount) {
        public ClassView(Long id, String code, String name, String semester,
                         String teacherName, long studentCount) {
            this(id, code, name, semester, null, null, null, teacherName, studentCount);
        }
    }

    public record StudentView(String studentId, String name, Instant enrolledAt) {}

    public record ClassDetail(ClassView info, List<StudentView> students) {}

    public record AddStudentsRequest(@NotEmpty @Size(max = 500) List<@NotBlank String> studentIds) {}

    /** notFound gồm cả mã không tồn tại lẫn mã không phải sinh viên. */
    public record AddStudentsResult(List<String> added, List<String> alreadyEnrolled, List<String> notFound) {}

    public record ReportSession(Long id, String title, Instant openedAt, long presentCount) {}

    public record ReportRow(String studentId, String studentName, int attended, int total, double ratePercent) {}

    public record ClassReport(ClassView info, List<ReportSession> sessions, List<ReportRow> rows) {}

    // ---------- Buổi điểm danh ----------
    public record OpenSessionRequest(
            @NotNull Long classId,
            @NotBlank @Size(max = 200) String title,
            @Min(1) Integer durationMinutes,
            @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @Min(10) @Max(5000) Integer radiusMeters) {}

    /** Thông tin buổi học dành cho sinh viên; cố ý không trả tọa độ để tránh làm giả vị trí. */
    public record SessionView(Long id, Long classId, String classCode, String className, String title,
                              String teacherName, Instant openedAt, Instant expiresAt,
                              boolean locationRequired) {}

    public record TeacherSessionView(Long id, Long classId, String classCode, String className, String title,
                                     Instant openedAt, Instant expiresAt, Instant closedAt, boolean active,
                                     Double latitude, Double longitude, Integer radiusMeters,
                                     long presentCount) {}

    public record RecordView(String studentId, String studentName, Instant checkedAt,
                             Double similarity, String method) {}

    /** absent: sinh viên trong danh sách lớp nhưng chưa có mặt (không tính người được thêm vào lớp sau khi buổi mở). */
    public record SessionRecords(TeacherSessionView session, List<RecordView> records,
                                 List<StudentView> absent, int rosterSize) {}

    public record ManualMarkRequest(@NotBlank String studentId) {}

    // ---------- Điểm danh ----------
    public record AttendanceRequest(
            @NotNull Long sessionId,
            @NotNull @Size(min = 16, max = 2048) float[] embedding,
            @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @DecimalMin("-180") @DecimalMax("180") Double longitude) {}

    /** similarity chỉ có khi thành công, để kẻ gian không dò dần theo độ khớp. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AttendanceResult(boolean matched, Double similarity, String message, Integer attemptsLeft) {}

    public record HistoryView(Long sessionId, String sessionTitle, Instant checkedAt,
                              Double similarity, String method) {}

    // ---------- Thời khóa biểu & Gemini AI ----------
    public record TimetableItemDto(
            int dayOfWeek,
            String date,
            String shift,
            String subjectName,
            String subjectCode,
            String lecturer,
            String room,
            String note) {}

    public record GeminiScanRequest(String imageBase64) {}

    public record GeminiScanResponse(List<TimetableItemDto> items, String message) {}
}
