package com.example.demo.controller;

import com.example.demo.model.AttendanceRecord;
import com.example.demo.repository.AttendanceRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AttendanceController {

    @Autowired
    private AttendanceRecordRepository recordRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/upload_face.php")
    public ResponseEntity<?> uploadFace(@RequestParam("image") MultipartFile file, @RequestParam(value = "username", defaultValue = "unknown") String username) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Không tìm thấy file ảnh"));
        }

        try {
            // Lưu ảnh vào thư mục cấu hình bên ngoài container Spring Boot
            String fileName = username + "_" + System.currentTimeMillis() + ".jpg";
            Path uploadPath = Paths.get("../uploads/");
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            Path filePath = uploadPath.resolve(fileName);
            file.transferTo(filePath.toFile());

            // Chuyển tiếp tới AI Engine đang chạy ở cổng 5000
            String aiUrl = "http://ai_engine:5000/verify";
            Map<String, String> requestData = Map.of(
                    "uploaded_img", "/app/uploads/" + fileName,
                    "original_img", "/app/dataset/" + username + ".jpg"
            );

            ResponseEntity aiResponse = restTemplate.postForEntity(aiUrl, requestData, Map.class);

            if (aiResponse.getStatusCode().is2xxSuccessful() && aiResponse.getBody() != null) {
                Map<String, Object> aiResult = aiResponse.getBody();

                if ("success".equals(aiResult.get("status")) && Boolean.TRUE.equals(aiResult.get("verified"))) {
                    AttendanceRecord record = new AttendanceRecord();
                    record.setUsername(username);
                    record.setImagePath("uploads/" + fileName);
                    record.setStatus("success");
                    recordRepository.save(record);

                    return ResponseEntity.ok(Map.of(
                            "status", "success",
                            "message", "Điểm danh thành công! (Sai số: " + aiResult.get("distance") + ")"
                    ));
                } else {
                    return ResponseEntity.badRequest().body(Map.of("status", "error", "message", "Khuôn mặt không khớp! Vui lòng thử lại."));
                }
            } else {
                return ResponseEntity.status(500).body(Map.of("status", "error", "message", "Lỗi xử lý từ AI Engine."));
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("status", "error", "message", "Lỗi hệ thống: " + e.getMessage()));
        }
    }
}