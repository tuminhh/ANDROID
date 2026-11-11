package com.uni.attendance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Một buổi điểm danh do giảng viên mở. */
@Entity
@Table(name = "attendance_sessions")
public class AttendanceSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "teacher_id", nullable = false, length = 32)
    private String teacherId;

    /** Lớp học phần của buổi này. Null chỉ với dữ liệu cũ tạo trước khi có lớp học phần. */
    @Column(name = "class_id")
    private Long classId;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    private Double latitude;
    private Double longitude;

    @Column(name = "radius_meters")
    private Integer radiusMeters;

    protected AttendanceSession() {}

    public AttendanceSession(String title, String teacherId, Long classId, Instant openedAt, Instant expiresAt) {
        this.title = title;
        this.teacherId = teacherId;
        this.classId = classId;
        this.openedAt = openedAt;
        this.expiresAt = expiresAt;
    }

    public void setLocation(double latitude, double longitude, int radiusMeters) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusMeters = radiusMeters;
    }

    public boolean hasLocation() {
        return latitude != null && longitude != null && radiusMeters != null;
    }

    /** Đang mở khi chưa bị đóng thủ công và chưa hết thời hạn. */
    public boolean isActiveAt(Instant now) {
        return closedAt == null && now.isBefore(expiresAt);
    }

    public void close(Instant now) {
        if (closedAt == null) {
            closedAt = now;
        }
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getTeacherId() { return teacherId; }
    public Long getClassId() { return classId; }
    public Instant getOpenedAt() { return openedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getClosedAt() { return closedAt; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Integer getRadiusMeters() { return radiusMeters; }
}
