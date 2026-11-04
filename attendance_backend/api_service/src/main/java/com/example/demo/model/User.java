package com.example.demo.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;
    private String password;
    @Column(name = "full_name")
    private String fullName;
    private String role;

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getRole() { return role; }
}