package com.example.demo.model;

import jakarta.persistence.*;

@Entity
@Table(name = "attendance_records")
public class AttendanceRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;
    @Column(name = "image_path")
    private String imagePath;
    private String status;

    public void setUsername(String username) { this.username = username; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public void setStatus(String status) { this.status = status; }
}