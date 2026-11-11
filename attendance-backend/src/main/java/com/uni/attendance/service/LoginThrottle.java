package com.uni.attendance.service;

import com.uni.attendance.config.AppProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Khóa tạm thời đăng nhập sau nhiều lần sai liên tiếp (chống dò mật khẩu).
 * Lưu trong bộ nhớ nên sẽ mất khi khởi động lại và không dùng chung giữa nhiều máy chủ;
 * khi chạy nhiều bản, chuyển sang Redis hoặc bảng CSDL.
 */
@Component
public class LoginThrottle {
    private static final int MAX_TRACKED_KEYS = 10_000;

    private record State(int failures, Instant lastFailure, Instant lockedUntil) {}

    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration window;

    public LoginThrottle(AppProperties props) {
        this.maxFailures = props.login().maxFailures();
        this.window = Duration.ofMinutes(props.login().lockMinutes());
    }

    public boolean isLocked(String key) {
        State s = states.get(key);
        return s != null && s.lockedUntil() != null && Instant.now().isBefore(s.lockedUntil());
    }

    public void recordFailure(String key) {
        Instant now = Instant.now();
        if (states.size() > MAX_TRACKED_KEYS) {
            states.entrySet().removeIf(e -> isStale(e.getValue(), now));
        }
        states.compute(key, (k, s) -> {
            int failures = (s == null || isStale(s, now)) ? 1 : s.failures() + 1;
            Instant lockedUntil = failures >= maxFailures ? now.plus(window) : null;
            return new State(failures, now, lockedUntil);
        });
    }

    public void recordSuccess(String key) {
        states.remove(key);
    }

    private boolean isStale(State s, Instant now) {
        Instant until = s.lockedUntil() != null ? s.lockedUntil() : s.lastFailure().plus(window);
        return now.isAfter(until);
    }
}
