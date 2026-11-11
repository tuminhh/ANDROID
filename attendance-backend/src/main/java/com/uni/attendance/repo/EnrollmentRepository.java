package com.uni.attendance.repo;

import com.uni.attendance.domain.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    boolean existsByClassIdAndStudentId(Long classId, String studentId);

    Optional<Enrollment> findByClassIdAndStudentId(Long classId, String studentId);

    List<Enrollment> findByClassIdOrderByStudentIdAsc(Long classId);

    List<Enrollment> findByStudentId(String studentId);

    long countByClassId(Long classId);

    void deleteByClassId(Long classId);
}
