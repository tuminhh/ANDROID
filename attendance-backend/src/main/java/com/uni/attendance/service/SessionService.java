package com.uni.attendance.service;

import com.uni.attendance.config.AppProperties;
import com.uni.attendance.domain.AttendanceRecord;
import com.uni.attendance.domain.AttendanceSession;
import com.uni.attendance.domain.CourseClass;
import com.uni.attendance.domain.Enrollment;
import com.uni.attendance.domain.Role;
import com.uni.attendance.domain.User;
import com.uni.attendance.repo.ClassRepository;
import com.uni.attendance.repo.EnrollmentRepository;
import com.uni.attendance.repo.RecordRepository;
import com.uni.attendance.repo.SessionRepository;
import com.uni.attendance.repo.UserRepository;
import com.uni.attendance.util.Csv;
import com.uni.attendance.util.Xlsx;
import com.uni.attendance.util.Xlsx.Style;
import com.uni.attendance.web.ApiException;
import com.uni.attendance.web.Dtos.OpenSessionRequest;
import com.uni.attendance.web.Dtos.RecordView;
import com.uni.attendance.web.Dtos.SessionRecords;
import com.uni.attendance.web.Dtos.SessionView;
import com.uni.attendance.web.Dtos.StudentView;
import com.uni.attendance.web.Dtos.TeacherSessionView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SessionService {
    private static final DateTimeFormatter CSV_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final SessionRepository sessions;
    private final RecordRepository records;
    private final UserRepository users;
    private final ClassRepository classes;
    private final EnrollmentRepository enrollments;
    private final AppProperties props;

    public SessionService(SessionRepository sessions, RecordRepository records, UserRepository users,
                          ClassRepository classes, EnrollmentRepository enrollments, AppProperties props) {
        this.sessions = sessions;
        this.records = records;
        this.users = users;
        this.classes = classes;
        this.enrollments = enrollments;
        this.props = props;
    }

    /** Mở buổi cho một lớp của giảng viên; các buổi đang mở khác của cùng giảng viên được đóng lại. */
    @Transactional
    public TeacherSessionView open(String teacherId, OpenSessionRequest r) {
        CourseClass clazz = classes.findById(r.classId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CLASS_NOT_FOUND", "Không tìm thấy lớp học phần"));
        if (!clazz.getTeacherId().equals(teacherId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_OWNER", "Đây không phải lớp học phần của bạn");
        }
        int minutes = r.durationMinutes() == null ? props.session().defaultMinutes() : r.durationMinutes();
        if (minutes < 1 || minutes > props.session().maxMinutes()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DURATION",
                    "Thời lượng phải từ 1 đến " + props.session().maxMinutes() + " phút");
        }
        boolean anyLocation = r.latitude() != null || r.longitude() != null || r.radiusMeters() != null;
        boolean allLocation = r.latitude() != null && r.longitude() != null && r.radiusMeters() != null;
        if (anyLocation && !allLocation) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_LOCATION",
                    "Cần nhập đủ vĩ độ, kinh độ và bán kính");
        }

        Instant now = Instant.now();
        sessions.findActiveByTeacher(teacherId, now).forEach(s -> s.close(now));

        AttendanceSession session = new AttendanceSession(
                r.title().trim(), teacherId, clazz.getId(), now, now.plus(Duration.ofMinutes(minutes)));
        if (allLocation) {
            session.setLocation(r.latitude(), r.longitude(), r.radiusMeters());
        }
        return teacherView(sessions.save(session), now, Map.of(clazz.getId(), clazz));
    }

    @Transactional
    public TeacherSessionView close(String teacherId, Long sessionId) {
        AttendanceSession session = owned(teacherId, sessionId);
        Instant now = Instant.now();
        session.close(now);
        return teacherView(session, now, classMap(Collections.singletonList(session.getClassId())));
    }

    /** Các buổi đang mở của những lớp mà sinh viên có tên trong danh sách. */
    @Transactional(readOnly = true)
    public List<SessionView> listActive(String studentId) {
        List<Long> classIds = enrollments.findByStudentId(studentId).stream().map(Enrollment::getClassId).toList();
        if (classIds.isEmpty()) {
            return List.of();
        }
        List<AttendanceSession> active = sessions.findActiveForClasses(Instant.now(), classIds);
        Map<String, String> teacherNames = names(active.stream().map(AttendanceSession::getTeacherId).toList());
        Map<Long, CourseClass> classMap = classMap(active.stream().map(AttendanceSession::getClassId).toList());
        return active.stream()
                .map(s -> {
                    CourseClass c = classMap.get(s.getClassId());
                    return new SessionView(s.getId(), s.getClassId(),
                            c == null ? "" : c.getCode(), c == null ? "" : c.getName(), s.getTitle(),
                            teacherNames.getOrDefault(s.getTeacherId(), ""),
                            s.getOpenedAt(), s.getExpiresAt(), s.hasLocation());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TeacherSessionView> mine(String teacherId) {
        Instant now = Instant.now();
        List<AttendanceSession> list = sessions.findByTeacherIdOrderByOpenedAtDesc(teacherId);
        Map<Long, CourseClass> classMap = classMap(list.stream().map(AttendanceSession::getClassId).toList());
        return list.stream().map(s -> teacherView(s, now, classMap)).toList();
    }

    @Transactional(readOnly = true)
    public SessionRecords recordsOf(String teacherId, Long sessionId) {
        AttendanceSession session = owned(teacherId, sessionId);
        List<AttendanceRecord> list = records.findBySessionIdOrderByCheckedAtAsc(sessionId);
        List<Enrollment> roster = session.getClassId() == null
                ? List.of() : enrollments.findByClassIdOrderByStudentIdAsc(session.getClassId());

        Set<String> allIds = new HashSet<>();
        list.forEach(r -> allIds.add(r.getStudentId()));
        roster.forEach(e -> allIds.add(e.getStudentId()));
        Map<String, String> names = names(allIds);

        Set<String> present = list.stream().map(AttendanceRecord::getStudentId).collect(Collectors.toSet());
        List<RecordView> views = list.stream()
                .map(r -> new RecordView(r.getStudentId(), names.getOrDefault(r.getStudentId(), ""),
                        r.getCheckedAt(), r.getSimilarity(), r.getMethod().name()))
                .toList();
        // Người được thêm vào lớp sau khi buổi đã mở thì không bị tính là vắng
        List<StudentView> absent = roster.stream()
                .filter(e -> !present.contains(e.getStudentId()) && !e.getEnrolledAt().isAfter(session.getOpenedAt()))
                .map(e -> new StudentView(e.getStudentId(), names.getOrDefault(e.getStudentId(), ""), e.getEnrolledAt()))
                .toList();

        TeacherSessionView view = teacherView(session, Instant.now(),
                classMap(Collections.singletonList(session.getClassId())));
        return new SessionRecords(view, views, absent, roster.size());
    }

    @Transactional(readOnly = true)
    public String exportCsv(String teacherId, Long sessionId) {
        SessionRecords data = recordsOf(teacherId, sessionId);
        StringBuilder sb = new StringBuilder();
        sb.append(Csv.row("Buổi học", data.session().title()));
        sb.append(Csv.row("Lớp học phần", data.session().classCode() == null ? "" : data.session().classCode()));
        sb.append(Csv.row("Mã SV", "Họ tên", "Thời gian", "Độ khớp", "Hình thức"));
        for (RecordView r : data.records()) {
            sb.append(Csv.row(r.studentId(), r.studentName(), CSV_TIME.format(r.checkedAt()),
                    r.similarity() == null ? "" : String.format(Locale.ROOT, "%.4f", r.similarity()),
                    r.method()));
        }
        for (StudentView s : data.absent()) {
            sb.append(Csv.row(s.studentId(), s.name(), "", "", "VẮNG"));
        }
        return sb.toString();
    }

    /** Danh sách một buổi dạng Excel: có mặt tô xanh, vắng tô đỏ, kèm tổng kết. */
    @Transactional(readOnly = true)
    public byte[] exportXlsx(String teacherId, Long sessionId) {
        SessionRecords data = recordsOf(teacherId, sessionId);
        String classCode = data.session().classCode() == null ? "" : data.session().classCode();
        String className = data.session().className() == null ? "" : data.session().className();
        Xlsx x = new Xlsx("Điểm danh").widths(6, 14, 28, 20, 12, 14);
        x.row(Style.TITLE, "DANH SÁCH ĐIỂM DANH");
        x.row(Style.NORMAL, "Lớp học phần: " + classCode + (className.isEmpty() ? "" : " - " + className));
        x.row(Style.NORMAL, "Buổi học: " + data.session().title());
        x.row(Style.NORMAL, "Giảng viên: " + users.findById(teacherId).map(User::getName).orElse(""));
        x.row(Style.NORMAL, "Mở lúc: " + CSV_TIME.format(data.session().openedAt()));
        x.row(Style.NORMAL, "Có mặt: " + data.records().size() + "/" + data.rosterSize()
                + " - Vắng: " + data.absent().size());
        x.blank();
        x.row(Style.HEADER, "STT", "Mã SV", "Họ tên", "Thời gian", "Hình thức", "Trạng thái");
        int n = 0;
        for (RecordView r : data.records()) {
            n++;
            x.row(Style.PRESENT, n, r.studentId(), r.studentName(), CSV_TIME.format(r.checkedAt()),
                    "MANUAL".equals(r.method()) ? "Thủ công" : "Khuôn mặt", "Có mặt");
        }
        for (StudentView s : data.absent()) {
            n++;
            x.row(Style.ABSENT, n, s.studentId(), s.name(), "", "", "Vắng");
        }
        return x.build();
    }

    /** Giảng viên đánh dấu có mặt thủ công (ví dụ sinh viên bị khóa hoặc máy hỏng). Sinh viên phải thuộc lớp. */
    @Transactional
    public RecordView markManually(String teacherId, Long sessionId, String studentIdRaw) {
        AttendanceSession session = owned(teacherId, sessionId);
        String studentId = AuthService.normalizeId(studentIdRaw);
        User student = users.findById(studentId)
                .filter(u -> u.getRole() == Role.STUDENT)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "STUDENT_NOT_FOUND", "Không tìm thấy sinh viên"));
        if (session.getClassId() == null
                || !enrollments.existsByClassIdAndStudentId(session.getClassId(), studentId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_ENROLLED",
                    "Sinh viên không thuộc lớp học phần của buổi này. Hãy thêm vào danh sách lớp trước");
        }
        if (records.existsBySessionIdAndStudentId(sessionId, studentId)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_MARKED", "Sinh viên đã có mặt trong buổi này");
        }
        AttendanceRecord saved = records.save(new AttendanceRecord(
                sessionId, studentId, Instant.now(), null, null, AttendanceRecord.Method.MANUAL));
        return new RecordView(studentId, student.getName(), saved.getCheckedAt(), null, saved.getMethod().name());
    }

    @Transactional
    public void deleteMark(String teacherId, Long sessionId, String studentIdRaw) {
        owned(teacherId, sessionId);
        AttendanceRecord record = records
                .findBySessionIdAndStudentId(sessionId, AuthService.normalizeId(studentIdRaw))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RECORD_NOT_FOUND", "Không có bản ghi điểm danh này"));
        records.delete(record);
    }

    private AttendanceSession owned(String teacherId, Long sessionId) {
        AttendanceSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Không tìm thấy buổi điểm danh"));
        if (!session.getTeacherId().equals(teacherId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_OWNER", "Đây không phải buổi điểm danh của bạn");
        }
        return session;
    }

    private TeacherSessionView teacherView(AttendanceSession s, Instant now, Map<Long, CourseClass> classMap) {
        CourseClass c = s.getClassId() == null ? null : classMap.get(s.getClassId());
        return new TeacherSessionView(s.getId(), s.getClassId(),
                c == null ? null : c.getCode(), c == null ? null : c.getName(), s.getTitle(),
                s.getOpenedAt(), s.getExpiresAt(), s.getClosedAt(), s.isActiveAt(now),
                s.getLatitude(), s.getLongitude(), s.getRadiusMeters(), records.countBySessionId(s.getId()));
    }

    /** Bỏ qua id null (buổi cũ chưa gắn lớp). */
    private Map<Long, CourseClass> classMap(Collection<Long> ids) {
        Set<Long> clean = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, CourseClass> map = new HashMap<>();
        if (!clean.isEmpty()) {
            for (CourseClass c : classes.findAllById(clean)) {
                map.put(c.getId(), c);
            }
        }
        return map;
    }

    private Map<String, String> names(Collection<String> ids) {
        Map<String, String> map = new HashMap<>();
        for (User u : users.findAllById(ids)) {
            map.put(u.getUserId(), u.getName());
        }
        return map;
    }
}
