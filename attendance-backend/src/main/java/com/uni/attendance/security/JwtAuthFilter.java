package com.uni.attendance.security;

import com.uni.attendance.repo.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Đọc "Authorization: Bearer ...", kiểm tra token và nạp người dùng từ CSDL.
 * Vai trò lấy từ CSDL chứ không tin vào claim trong token.
 * Không đánh dấu @Component để tránh Spring Boot đăng ký bộ lọc này hai lần.
 */
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserRepository users;

    public JwtAuthFilter(JwtService jwtService, UserRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parse(header.substring(7).trim());
                users.findById(claims.getSubject()).ifPresent(user -> {
                    var auth = new UsernamePasswordAuthenticationToken(
                            new AuthUser(user.getUserId(), user.getRole()), null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });
            } catch (JwtException | IllegalArgumentException e) {
                // Token không hợp lệ: coi như chưa đăng nhập, lớp bảo mật sẽ trả 401.
            }
        }
        chain.doFilter(request, response);
    }
}
