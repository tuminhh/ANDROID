package com.uni.attendance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Toàn bộ tham số nghiệp vụ, đọc từ khóa "app" trong application.yml. */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, String teacherInviteCode, Face face, Session session, Login login, Gemini gemini) {

    public record Jwt(String secret, int expirationHours) {}

    public record Face(double threshold, int dimension, int maxFailedAttempts) {}

    public record Session(int defaultMinutes, int maxMinutes) {}

    public record Login(int maxFailures, int lockMinutes) {}

    public record Gemini(String apiKey, String model) {}
}
