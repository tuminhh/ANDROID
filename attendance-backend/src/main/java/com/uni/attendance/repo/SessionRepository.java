package com.uni.attendance.repo;

import com.uni.attendance.domain.AttendanceSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface SessionRepository extends JpaRepository<AttendanceSession, Long> {

    List<AttendanceSession> findByTeacherIdOrderByOpenedAtDesc(String teacherId);

    List<AttendanceSession> findByClassIdOrderByOpenedAtAsc(Long classId);

    /** Các buổi đang mở thuộc những lớp cho trước. Danh sách lớp không được rỗng. */
    @Query("select s from AttendanceSession s "
            + "where s.closedAt is null and s.expiresAt > :now and s.classId in :classIds "
            + "order by s.openedAt desc")
    List<AttendanceSession> findActiveForClasses(@Param("now") Instant now,
                                                 @Param("classIds") Collection<Long> classIds);

    @Query("select s from AttendanceSession s "
            + "where s.teacherId = :teacherId and s.closedAt is null and s.expiresAt > :now")
    List<AttendanceSession> findActiveByTeacher(@Param("teacherId") String teacherId,
                                                @Param("now") Instant now);
}
