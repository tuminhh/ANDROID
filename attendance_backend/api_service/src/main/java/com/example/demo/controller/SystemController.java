package com.example.demo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SystemController {

    @GetMapping("/connect.php")
    public ResponseEntity<?> checkConnection() {
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Kết nối thành công từ Android đến Docker (Java Spring Boot + MySQL)!"
        ));
    }
}