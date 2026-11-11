package com.uni.attendance.web;

import com.uni.attendance.security.AuthUser;
import com.uni.attendance.service.AttendanceService;
import com.uni.attendance.web.Dtos.AttendanceRequest;
import com.uni.attendance.web.Dtos.AttendanceResult;
import com.uni.attendance.web.Dtos.HistoryView;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/attendance")
@PreAuthorize("hasRole('STUDENT')")
public class AttendanceController {
    private final AttendanceService attendance;

    public AttendanceController(AttendanceService attendance) {
        this.attendance = attendance;
    }

    @PostMapping
    public AttendanceResult submit(@AuthenticationPrincipal AuthUser user,
                                   @Valid @RequestBody AttendanceRequest request) {
        return attendance.submit(user.userId(), request);
    }

    @GetMapping("/history")
    public List<HistoryView> history(@AuthenticationPrincipal AuthUser user) {
        return attendance.history(user.userId());
    }
}
