package com.uni.attendance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Vector đặc trưng khuôn mặt của một sinh viên. Không lưu ảnh gốc và không bao giờ trả ra qua API. */
@Entity
@Table(name = "face_templates")
public class FaceTemplate {
    @Id
    @Column(name = "user_id", length = 32)
    private String userId;

    @Column(nullable = false, length = 16384)
    private String embedding;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected FaceTemplate() {}

    public FaceTemplate(String userId, String embedding) {
        this.userId = userId;
        this.embedding = embedding;
    }

    public String getUserId() { return userId; }
    public String getEmbedding() { return embedding; }

    public void replace(String embedding) {
        this.embedding = embedding;
        this.updatedAt = Instant.now();
    }
}
