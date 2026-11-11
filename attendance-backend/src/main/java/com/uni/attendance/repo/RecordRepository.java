package com.uni.attendance.repo;

import com.uni.attendance.domain.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RecordRepository extends JpaRepository<AttendanceRecord, Long> {

    boolean existsBySessionIdAndStudentId(Long sessionId, String studentId);

    Optional<AttendanceRecord> findBySessionIdAndStudentId(Long sessionId, String studentId);

    List<AttendanceRecord> findBySessionIdOrderByCheckedAtAsc(Long sessionId);

    List<AttendanceRecord> findBySessionIdIn(Collection<Long> sessionIds);

    List<AttendanceRecord> findByStudentIdOrderByCheckedAtDesc(String studentId);

    long countBySessionId(Long sessionId);

    void deleteBySessionId(Long sessionId);

    void deleteBySessionIdIn(Collection<Long> sessionIds);
}
