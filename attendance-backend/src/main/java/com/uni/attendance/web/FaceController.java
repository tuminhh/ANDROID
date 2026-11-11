package com.uni.attendance.web;

import com.uni.attendance.security.AuthUser;
import com.uni.attendance.service.FaceService;
import com.uni.attendance.web.Dtos.FaceEnrollRequest;
import com.uni.attendance.web.Dtos.FaceStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/face")
@PreAuthorize("hasRole('STUDENT')")
public class FaceController {
    private final FaceService faceService;

    public FaceController(FaceService faceService) {
        this.faceService = faceService;
    }

    /** Đăng ký (hoặc đăng ký lại) khuôn mặt bằng vector trung bình do app tính ra. */
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void enroll(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody FaceEnrollRequest request) {
        faceService.enroll(user.userId(), request);
    }

    @GetMapping("/status")
    public FaceStatus status(@AuthenticationPrincipal AuthUser user) {
        return faceService.status(user.userId());
    }
}
