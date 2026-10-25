package com.example.smartattendance.models;

public class LoginResponse {
    private String status;
    private String message;
    private User data;

    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public User getData() { return data; }
}