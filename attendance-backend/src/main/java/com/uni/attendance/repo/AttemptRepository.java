package com.uni.attendance.repo;

import com.uni.attendance.domain.AttendanceAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptRepository extends JpaRepository<AttendanceAttempt, Long> {

    long countBySessionIdAndStudentIdAndSuccessFalse(Long sessionId, String studentId);
}
