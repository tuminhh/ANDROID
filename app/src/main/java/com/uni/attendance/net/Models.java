package com.uni.attendance.net;

import java.util.List;
import java.util.Map;

/** Các đối tượng JSON trao đổi với backend (tên trường phải trùng với backend). */
public final class Models {
    private Models() {}

    // ---------- Xác thực ----------
    public static class RegisterRequest {
        public String userId, name, password, role, inviteCode;

        public RegisterRequest(String userId, String name, String password, String role, String inviteCode) {
            this.userId = userId;
            this.name = name;
            this.password = password;
            this.role = role;
            this.inviteCode = inviteCode;
        }
    }

    public static class LoginRequest {
        public String userId, password;

        public LoginRequest(String userId, String password) {
            this.userId = userId;
            this.password = password;
        }
    }

    public static class User {
        public String userId, name, role;
        public boolean faceEnrolled;
    }

    public static class AuthResponse {
        public String token;
        public User user;
    }

    // ---------- Khuôn mặt ----------
    public static class FaceEnrollRequest {
        public float[] embedding;
        public String password; // chỉ cần khi đăng ký lại

        public FaceEnrollRequest(float[] embedding, String password) {
            this.embedding = embedding;
            this.password = password;
        }
    }

    // ---------- Lớp học phần ----------
    public static class CreateClassRequest {
        public String code, name, semester;

        public CreateClassRequest(String code, String name, String semester) {
            this.code = code;
            this.name = name;
            this.semester = semester;
        }
    }

    public static class ClassView {
        public long id;
        public String code, name, semester, teacherName;
        public Integer dayOfWeek;
        public String shift, room;
        public long studentCount;
    }

    public static class BatchClassImportItem {
        public String code, name, semester;
        public Integer dayOfWeek;
        public String shift, room;
        public List<String> studentIds;

        public BatchClassImportItem() {}
        public BatchClassImportItem(String code, String name, String semester, Integer dayOfWeek, String shift, String room, List<String> studentIds) {
            this.code = code;
            this.name = name;
            this.semester = semester;
            this.dayOfWeek = dayOfWeek;
            this.shift = shift;
            this.room = room;
            this.studentIds = studentIds;
        }
    }

    public static class BatchClassImportResult {
        public int createdCount, updatedCount, totalStudentsEnrolled;
        public List<String> messages;
    }

    public static class StudentView {
        public String studentId, name, enrolledAt;
    }

    public static class ClassDetail {
        public ClassView info;
        public List<StudentView> students;
    }

    public static class AddStudentsRequest {
        public List<String> studentIds;

        public AddStudentsRequest(List<String> studentIds) {
            this.studentIds = studentIds;
        }
    }

    /** notFound gồm cả mã không tồn tại lẫn mã không phải sinh viên. */
    public static class AddStudentsResult {
        public List<String> added, alreadyEnrolled, notFound;
    }

    public static class ReportSession {
        public long id, presentCount;
        public String title, openedAt;
    }

    public static class ReportRow {
        public String studentId, studentName;
        public int attended, total;
        public double ratePercent;
    }

    public static class ClassReport {
        public ClassView info;
        public List<ReportSession> sessions;
        public List<ReportRow> rows;
    }

    // ---------- Buổi điểm danh ----------
    public static class OpenSessionRequest {
        public Long classId;
        public String title;
        public Integer durationMinutes;
        public Double latitude, longitude;
        public Integer radiusMeters;

        public OpenSessionRequest(long classId, String title, int durationMinutes, Double latitude,
                                  Double longitude, Integer radiusMeters) {
            this.classId = classId;
            this.title = title;
            this.durationMinutes = durationMinutes;
            this.latitude = latitude;
            this.longitude = longitude;
            this.radiusMeters = radiusMeters;
        }
    }

    public static class SessionView {
        public long id, classId;
        public String classCode, className, title, teacherName, openedAt, expiresAt;
        public boolean locationRequired;
    }

    public static class TeacherSessionView {
        public long id, presentCount;
        public Long classId;
        public String classCode, className, title, openedAt, expiresAt, closedAt;
        public boolean active;
        public Double latitude, longitude;
        public Integer radiusMeters;
    }

    public static class RecordView {
        public String studentId, studentName, checkedAt, method;
        public Double similarity;
    }

    /** absent: sinh viên trong lớp nhưng chưa có mặt (không gồm người được thêm vào lớp sau khi buổi mở). */
    public static class SessionRecords {
        public TeacherSessionView session;
        public List<RecordView> records;
        public List<StudentView> absent;
        public int rosterSize;
    }

    public static class ManualMarkRequest {
        public String studentId;

        public ManualMarkRequest(String studentId) {
            this.studentId = studentId;
        }
    }

    // ---------- Điểm danh ----------
    public static class AttendanceRequest {
        public long sessionId;
        public float[] embedding;
        public Double latitude, longitude;

        public AttendanceRequest(long sessionId, float[] embedding, Double latitude, Double longitude) {
            this.sessionId = sessionId;
            this.embedding = embedding;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    public static class AttendanceResult {
        public boolean matched;
        public Double similarity;
        public String message;
        public Integer attemptsLeft;
    }

    public static class HistoryView {
        public long sessionId;
        public String sessionTitle, checkedAt, method;
        public Double similarity;
    }

    // ---------- Thời khóa biểu & Gemini AI ----------
    public static class TimetableItemDto {
        public int dayOfWeek;
        public String date, shift, subjectName, subjectCode, lecturer, room, note;

        public TimetableItemDto() {}
        public TimetableItemDto(int dayOfWeek, String date, String shift, String subjectName,
                                String subjectCode, String lecturer, String room, String note) {
            this.dayOfWeek = dayOfWeek;
            this.date = date;
            this.shift = shift;
            this.subjectName = subjectName;
            this.subjectCode = subjectCode;
            this.lecturer = lecturer;
            this.room = room;
            this.note = note;
        }
    }

    public static class GeminiScanRequest {
        public String imageBase64;
        public GeminiScanRequest(String imageBase64) {
            this.imageBase64 = imageBase64;
        }
    }

    public static class GeminiScanResponse {
        public List<TimetableItemDto> items;
        public String message;
    }

    // ---------- Lỗi ----------
    public static class ApiError {
        public String code, message;
        public Map<String, String> fields;
    }
}
