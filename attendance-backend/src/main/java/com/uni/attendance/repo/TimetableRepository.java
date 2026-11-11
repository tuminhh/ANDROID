package com.uni.attendance.repo;

import com.uni.attendance.domain.TimetableEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface TimetableRepository extends JpaRepository<TimetableEntry, Long> {
    List<TimetableEntry> findByUserIdOrderByDayOfWeekAsc(String userId);

    @Transactional
    @Modifying
    @Query("DELETE FROM TimetableEntry t WHERE t.userId = :userId")
    void deleteByUserId(String userId);
}
