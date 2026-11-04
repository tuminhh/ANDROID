package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @PostMapping(value = "/login.php", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<?> login(@RequestParam("username") String username, @RequestParam("password") String password) {
        if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Vui lòng nhập đầy đủ thông tin"));
        }

        Optional<User> userOpt = userRepository.findByUsernameAndPassword(username, password);

        if (userOpt.isPresent()) {
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Đăng nhập thành công",
                    "data", userOpt.get()
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Sai tài khoản hoặc mật khẩu"));
        }
    }
}