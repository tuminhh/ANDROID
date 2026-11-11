package com.uni.attendance.service;

import com.uni.attendance.domain.Role;
import com.uni.attendance.domain.TimetableEntry;
import com.uni.attendance.domain.User;
import com.uni.attendance.repo.TimetableRepository;
import com.uni.attendance.repo.UserRepository;
import com.uni.attendance.web.Dtos.ClassView;
import com.uni.attendance.web.Dtos.TimetableItemDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class TimetableService {
    private final TimetableRepository timetableRepo;
    private final ClassService classService;
    private final UserRepository userRepo;

    public TimetableService(TimetableRepository timetableRepo, ClassService classService, UserRepository userRepo) {
        this.timetableRepo = timetableRepo;
        this.classService = classService;
        this.userRepo = userRepo;
    }

    /**
     * Lấy thời khóa biểu:
     * - Học sinh: Tự động cập nhật từ các lớp học phần được giáo viên thêm vào.
     * - Giáo viên: Tự động cập nhật từ lịch dạy các lớp do giáo viên phụ trách.
     */
    @Transactional(readOnly = true)
    public List<TimetableItemDto> getMyTimetable(String userId) {
        User user = userRepo.findById(userId).orElse(null);
        if (user != null && user.getRole() == Role.STUDENT) {
            List<ClassView> enrolled = classService.enrolledFor(userId);
            List<TimetableItemDto> fromClasses = new ArrayList<>();
            for (ClassView c : enrolled) {
                int dow = c.dayOfWeek() != null ? c.dayOfWeek() : 2;
                String shift = c.shift() != null && !c.shift().isBlank() ? c.shift() : "Buổi sáng - 4,5,6";
                String room = c.room() != null && !c.room().isBlank() ? c.room() : "Chưa xếp phòng";
                fromClasses.add(new TimetableItemDto(
                        dow,
                        "Lịch học",
                        shift,
                        c.name(),
                        c.code(),
                        c.teacherName(),
                        room,
                        "Tự động cập nhật theo lớp " + c.code()
                ));
            }
            // Sinh viên: Trả về chính xác các lớp sinh viên đó có tên trong danh sách lớp học phần
            if (!fromClasses.isEmpty()) {
                return fromClasses;
            }
            List<TimetableEntry> entries = timetableRepo.findByUserIdOrderByDayOfWeekAsc(userId);
            return entries.stream().map(this::toDto).toList();
        } else if (user != null && user.getRole() == Role.TEACHER) {
            List<ClassView> mine = classService.mine(userId);
            List<TimetableItemDto> fromClasses = new ArrayList<>();
            for (ClassView c : mine) {
                int dow = c.dayOfWeek() != null ? c.dayOfWeek() : 2;
                String shift = c.shift() != null && !c.shift().isBlank() ? c.shift() : "Buổi sáng - 4,5,6";
                String room = c.room() != null && !c.room().isBlank() ? c.room() : "Chưa xếp phòng";
                fromClasses.add(new TimetableItemDto(
                        dow,
                        "Lịch dạy",
                        shift,
                        c.name(),
                        c.code(),
                        "Giảng viên phụ trách: " + c.teacherName(),
                        room,
                        "Lớp giảng dạy • " + c.studentCount() + " sinh viên"
                ));
            }
            // Giảng viên: Trả về chính xác các lớp do giảng viên đó phụ trách
            if (!fromClasses.isEmpty()) {
                return fromClasses;
            }
            List<TimetableEntry> entries = timetableRepo.findByUserIdOrderByDayOfWeekAsc(userId);
            return entries.stream().map(this::toDto).toList();
        }

        return List.of();
    }

    @Transactional
    public List<TimetableItemDto> saveUserTimetable(String userId, List<TimetableItemDto> items) {
        timetableRepo.deleteByUserId(userId);
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        List<TimetableEntry> toSave = new ArrayList<>();
        for (TimetableItemDto dto : items) {
            toSave.add(new TimetableEntry(
                    userId,
                    dto.dayOfWeek(),
                    dto.date(),
                    dto.shift(),
                    dto.subjectName(),
                    dto.subjectCode(),
                    dto.lecturer(),
                    dto.room(),
                    dto.note()
            ));
        }

        List<TimetableEntry> saved = timetableRepo.saveAll(toSave);
        return saved.stream().map(this::toDto).toList();
    }

    @Transactional
    public void deleteMyTimetable(String userId) {
        timetableRepo.deleteByUserId(userId);
    }

    private TimetableItemDto toDto(TimetableEntry entry) {
        return new TimetableItemDto(
                entry.getDayOfWeek(),
                entry.getDate(),
                entry.getShift(),
                entry.getSubjectName(),
                entry.getSubjectCode(),
                entry.getLecturer(),
                entry.getRoom(),
                entry.getNote()
        );
    }
}
