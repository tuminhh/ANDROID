package com.uni.attendance.security;

import com.uni.attendance.domain.Role;

/** Người dùng đã xác thực, đặt trong SecurityContext. */
public record AuthUser(String userId, Role role) {
}
