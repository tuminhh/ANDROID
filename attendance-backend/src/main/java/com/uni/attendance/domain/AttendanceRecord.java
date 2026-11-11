package com.uni.attendance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** Kết quả có mặt của một sinh viên trong một buổi. Mỗi sinh viên chỉ có một bản ghi cho mỗi buổi. */
@Entity
@Table(name = "attendance_records",
        uniqueConstraints = @UniqueConstraint(name = "uk_record_session_student",
                columnNames = {"session_id", "student_id"}))
public class AttendanceRecord {
    public enum Method { FACE, MANUAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "student_id", nullable = false, length = 32)
    private String studentId;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    /** Độ tương đồng khuôn mặt; null với bản ghi điểm danh thủ công. */
    private Double similarity;

    @Column(name = "distance_meters")
    private Double distanceMeters;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Method method;

    protected AttendanceRecord() {}

    public AttendanceRecord(Long sessionId, String studentId, Instant checkedAt,
                            Double similarity, Double distanceMeters, Method method) {
        this.sessionId = sessionId;
        this.studentId = studentId;
        this.checkedAt = checkedAt;
        this.similarity = similarity;
        this.distanceMeters = distanceMeters;
        this.method = method;
    }

    public Long getId() { return id; }
    public Long getSessionId() { return sessionId; }
    public String getStudentId() { return studentId; }
    public Instant getCheckedAt() { return checkedAt; }
    public Double getSimilarity() { return similarity; }
    public Double getDistanceMeters() { return distanceMeters; }
    public Method getMethod() { return method; }
}
