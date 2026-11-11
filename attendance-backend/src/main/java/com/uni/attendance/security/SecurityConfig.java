package com.uni.attendance.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uni.attendance.repo.UserRepository;
import com.uni.attendance.web.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Đăng nhập tự xử lý bằng JWT; khai báo bean này để Spring Boot không tạo tài khoản mặc định. */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Không dùng UserDetailsService");
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService,
                                           UserRepository users, ObjectMapper mapper) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API không trạng thái, xác thực bằng header Bearer
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> write(res, mapper, HttpStatus.UNAUTHORIZED,
                                ApiError.of("UNAUTHORIZED", "Chưa đăng nhập hoặc phiên đã hết hạn")))
                        .accessDeniedHandler((req, res, e) -> write(res, mapper, HttpStatus.FORBIDDEN,
                                ApiError.of("FORBIDDEN", "Bạn không có quyền thực hiện thao tác này"))))
                .addFilterBefore(new JwtAuthFilter(jwtService, users), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void write(HttpServletResponse res, ObjectMapper mapper, HttpStatus status, ApiError body)
            throws IOException {
        res.setStatus(status.value());
        res.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(res.getOutputStream(), body);
    }
}
