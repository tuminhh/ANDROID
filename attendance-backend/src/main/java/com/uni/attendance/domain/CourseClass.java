package com.uni.attendance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** Lớp học phần do một giảng viên phụ trách. Mã lớp là duy nhất trong toàn hệ thống. */
@Entity
@Table(name = "course_classes",
        uniqueConstraints = @UniqueConstraint(name = "uk_class_code", columnNames = "code"))
public class CourseClass {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 50)
    private String semester;

    @Column(name = "day_of_week")
    private Integer dayOfWeek; // 2=T2 ... 7=T7, 8=CN

    @Column(length = 100)
    private String shift; // ví dụ: "Buổi sáng - 4,5,6"

    @Column(length = 200)
    private String room; // ví dụ: "402 - A9 - Cơ sở 1 - Khu A"

    @Column(name = "teacher_id", nullable = false, length = 32)
    private String teacherId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected CourseClass() {}

    public CourseClass(String code, String name, String semester, String teacherId) {
        this(code, name, semester, null, null, null, teacherId);
    }

    public CourseClass(String code, String name, String semester, Integer dayOfWeek,
                       String shift, String room, String teacherId) {
        this.code = code;
        this.name = name;
        this.semester = semester;
        this.dayOfWeek = dayOfWeek;
        this.shift = shift;
        this.room = room;
        this.teacherId = teacherId;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getSemester() { return semester; }
    public Integer getDayOfWeek() { return dayOfWeek; }
    public String getShift() { return shift; }
    public String getRoom() { return room; }
    public String getTeacherId() { return teacherId; }
    public Instant getCreatedAt() { return createdAt; }

    public void updateInfo(String name, String semester, Integer dayOfWeek, String shift, String room) {
        if (name != null && !name.isBlank()) this.name = name;
        if (semester != null) this.semester = semester;
        if (dayOfWeek != null) this.dayOfWeek = dayOfWeek;
        if (shift != null) this.shift = shift;
        if (room != null) this.room = room;
    }
}
