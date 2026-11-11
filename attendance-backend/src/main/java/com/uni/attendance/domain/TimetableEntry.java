package com.uni.attendance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Mục thời khóa biểu của người dùng (sinh viên hoặc giảng viên).
 */
@Entity
@Table(name = "timetable_entries", indexes = {
        @Index(name = "idx_timetable_user", columnList = "user_id"),
        @Index(name = "idx_timetable_user_day", columnList = "user_id, day_of_week")
})
public class TimetableEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 32)
    private String userId;

    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(length = 50)
    private String date;

    @Column(nullable = false, length = 100)
    private String shift;

    @Column(name = "subject_name", nullable = false, length = 200)
    private String subjectName;

    @Column(name = "subject_code", length = 50)
    private String subjectCode;

    @Column(length = 200)
    private String lecturer;

    @Column(length = 200)
    private String room;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected TimetableEntry() {}

    public TimetableEntry(String userId, int dayOfWeek, String date, String shift,
                          String subjectName, String subjectCode, String lecturer,
                          String room, String note) {
        this.userId = userId;
        this.dayOfWeek = dayOfWeek;
        this.date = date;
        this.shift = shift;
        this.subjectName = subjectName;
        this.subjectCode = subjectCode;
        this.lecturer = lecturer;
        this.room = room;
        this.note = note;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getUserId() { return userId; }
    public int getDayOfWeek() { return dayOfWeek; }
    public String getDate() { return date; }
    public String getShift() { return shift; }
    public String getSubjectName() { return subjectName; }
    public String getSubjectCode() { return subjectCode; }
    public String getLecturer() { return lecturer; }
    public String getRoom() { return room; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
