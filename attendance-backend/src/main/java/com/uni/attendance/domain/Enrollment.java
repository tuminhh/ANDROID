package com.uni.attendance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** Sinh viên thuộc danh sách của một lớp học phần. */
@Entity
@Table(name = "enrollments",
        uniqueConstraints = @UniqueConstraint(name = "uk_enrollment", columnNames = {"class_id", "student_id"}),
        indexes = @Index(name = "idx_enrollment_student", columnList = "student_id"))
public class Enrollment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_id", nullable = false)
    private Long classId;

    @Column(name = "student_id", nullable = false, length = 32)
    private String studentId;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt = Instant.now();

    protected Enrollment() {}

    public Enrollment(Long classId, String studentId) {
        this.classId = classId;
        this.studentId = studentId;
    }

    public Long getId() { return id; }
    public Long getClassId() { return classId; }
    public String getStudentId() { return studentId; }
    public Instant getEnrolledAt() { return enrolledAt; }
}
