package com.uni.attendance.repo;

import com.uni.attendance.domain.CourseClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClassRepository extends JpaRepository<CourseClass, Long> {

    boolean existsByCode(String code);

    java.util.Optional<CourseClass> findByCode(String code);

    List<CourseClass> findByTeacherIdOrderByCreatedAtDesc(String teacherId);
}
