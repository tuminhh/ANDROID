package com.uni.attendance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/** Nhật ký mỗi lần thử nhận diện, dùng để giới hạn số lần sai và truy vết gian lận. */
@Entity
@Table(name = "attendance_attempts",
        indexes = @Index(name = "idx_attempt_session_student", columnList = "session_id, student_id"))
public class AttendanceAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "student_id", nullable = false, length = 32)
    private String studentId;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    @Column(nullable = false)
    private double similarity;

    @Column(nullable = false)
    private boolean success;

    protected AttendanceAttempt() {}

    public AttendanceAttempt(Long sessionId, String studentId, Instant attemptedAt,
                             double similarity, boolean success) {
        this.sessionId = sessionId;
        this.studentId = studentId;
        this.attemptedAt = attemptedAt;
        this.similarity = similarity;
        this.success = success;
    }

    public Long getId() { return id; }
    public boolean isSuccess() { return success; }
}
