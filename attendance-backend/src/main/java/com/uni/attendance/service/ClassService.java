package com.uni.attendance.service;

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
import com.uni.attendance.web.Dtos.AddStudentsRequest;
import com.uni.attendance.web.Dtos.AddStudentsResult;
import com.uni.attendance.web.Dtos.BatchClassImportItem;
import com.uni.attendance.web.Dtos.BatchClassImportResult;
import com.uni.attendance.web.Dtos.ClassDetail;
import com.uni.attendance.web.Dtos.ClassReport;
import com.uni.attendance.web.Dtos.ClassView;
import com.uni.attendance.web.Dtos.CreateClassRequest;
import com.uni.attendance.web.Dtos.ReportRow;
import com.uni.attendance.web.Dtos.ReportSession;
import com.uni.attendance.web.Dtos.StudentView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Lớp học phần, danh sách sinh viên và báo cáo chuyên cần theo lớp. */
@Service
public class ClassService {
    private static final DateTimeFormatter COLUMN_TIME =
            DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final ClassRepository classes;
    private final EnrollmentRepository enrollments;
    private final UserRepository users;
    private final SessionRepository sessions;
    private final RecordRepository records;

    public ClassService(ClassRepository classes, EnrollmentRepository enrollments, UserRepository users,
                        SessionRepository sessions, RecordRepository records) {
        this.classes = classes;
        this.enrollments = enrollments;
        this.users = users;
        this.sessions = sessions;
        this.records = records;
    }

    @Transactional
    public ClassView create(String teacherId, CreateClassRequest r) {
        String code = r.code().trim().toUpperCase(Locale.ROOT);
        if (classes.existsByCode(code)) {
            throw new ApiException(HttpStatus.CONFLICT, "CLASS_EXISTS", "Mã lớp học phần đã tồn tại");
        }
        String semester = r.semester() == null || r.semester().isBlank() ? null : r.semester().trim();
        CourseClass saved = classes.save(new CourseClass(code, r.name().trim(), semester,
                r.dayOfWeek(), r.shift(), r.room(), teacherId));
        return view(saved, names(List.of(teacherId)), 0);
    }

    @Transactional
    public BatchClassImportResult importBatchClasses(String teacherId, List<BatchClassImportItem> items) {
        int created = 0;
        int updated = 0;
        int totalEnrolled = 0;
        List<String> messages = new ArrayList<>();

        if (items == null || items.isEmpty()) {
            return new BatchClassImportResult(0, 0, 0, List.of("Không có lớp học phần nào để nhập"));
        }

        for (BatchClassImportItem item : items) {
            String code = item.code().trim().toUpperCase(Locale.ROOT);
            String name = item.name().trim();
            String semester = item.semester() != null && !item.semester().isBlank() ? item.semester().trim() : null;
            Integer dayOfWeek = item.dayOfWeek();
            String shift = item.shift();
            String room = item.room();

            CourseClass clazz = classes.findByCode(code).orElse(null);
            if (clazz == null) {
                clazz = classes.save(new CourseClass(code, name, semester, dayOfWeek, shift, room, teacherId));
                created++;
                messages.add("Đã tạo mới lớp: " + code + " - " + name);
            } else {
                if (!clazz.getTeacherId().equals(teacherId)) {
                    messages.add("Bỏ qua mã lớp " + code + " vì thuộc về giảng viên khác");
                    continue;
                }
                clazz.updateInfo(name, semester, dayOfWeek, shift, room);
                classes.save(clazz);
                updated++;
                messages.add("Đã cập nhật lớp: " + code + " - " + name);
            }

            // Ghi danh sinh viên nếu có
            if (item.studentIds() != null && !item.studentIds().isEmpty()) {
                AddStudentsResult addRes = addStudents(teacherId, clazz.getId(), new AddStudentsRequest(item.studentIds()));
                totalEnrolled += addRes.added().size();
            }
        }

        return new BatchClassImportResult(created, updated, totalEnrolled, messages);
    }

    @Transactional(readOnly = true)
    public List<ClassView> mine(String teacherId) {
        Map<String, String> names = names(List.of(teacherId));
        return classes.findByTeacherIdOrderByCreatedAtDesc(teacherId).stream()
                .map(c -> view(c, names, enrollments.countByClassId(c.getId())))
                .toList();
    }

    /** Các lớp mà sinh viên có tên trong danh sách. */
    @Transactional(readOnly = true)
    public List<ClassView> enrolledFor(String studentId) {
        List<Long> ids = enrollments.findByStudentId(studentId).stream().map(Enrollment::getClassId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        List<CourseClass> list = classes.findAllById(ids);
        Map<String, String> names = names(list.stream().map(CourseClass::getTeacherId).toList());
        return list.stream()
                .sorted(Comparator.comparing(CourseClass::getCode))
                .map(c -> view(c, names, enrollments.countByClassId(c.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ClassDetail detail(String teacherId, Long classId) {
        CourseClass clazz = owned(teacherId, classId);
        List<Enrollment> roster = enrollments.findByClassIdOrderByStudentIdAsc(classId);
        Map<String, String> names = names(roster.stream().map(Enrollment::getStudentId).toList());
        List<StudentView> students = roster.stream()
                .map(e -> new StudentView(e.getStudentId(), names.getOrDefault(e.getStudentId(), ""), e.getEnrolledAt()))
                .toList();
        return new ClassDetail(view(clazz, names(List.of(teacherId)), roster.size()), students);
    }

    /** Thêm hàng loạt theo mã sinh viên; mã sai hoặc đã có trong lớp được báo lại chứ không làm hỏng cả lô. */
    @Transactional
    public AddStudentsResult addStudents(String teacherId, Long classId, AddStudentsRequest r) {
        owned(teacherId, classId);
        Set<String> wanted = new LinkedHashSet<>();
        for (String raw : r.studentIds()) {
            String id = AuthService.normalizeId(raw);
            if (!id.isEmpty()) {
                wanted.add(id);
            }
        }
        List<String> added = new ArrayList<>();
        List<String> already = new ArrayList<>();
        List<String> notFound = new ArrayList<>();
        for (String id : wanted) {
            boolean isStudent = users.findById(id).filter(u -> u.getRole() == Role.STUDENT).isPresent();
            if (!isStudent) {
                notFound.add(id);
            } else if (enrollments.existsByClassIdAndStudentId(classId, id)) {
                already.add(id);
            } else {
                enrollments.save(new Enrollment(classId, id));
                added.add(id);
            }
        }
        return new AddStudentsResult(added, already, notFound);
    }

    /** Bỏ sinh viên khỏi danh sách lớp. Các bản ghi điểm danh cũ của họ vẫn được giữ. */
    @Transactional
    public void removeStudent(String teacherId, Long classId, String studentIdRaw) {
        owned(teacherId, classId);
        Enrollment enrollment = enrollments
                .findByClassIdAndStudentId(classId, AuthService.normalizeId(studentIdRaw))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_IN_CLASS", "Sinh viên không có trong lớp này"));
        enrollments.delete(enrollment);
    }

    /** Xóa lớp học phần: xóa tất cả bản ghi điểm danh, buổi học, danh sách ghi danh và lớp. */
    @Transactional
    public void deleteClass(String teacherId, Long classId) {
        CourseClass clazz = owned(teacherId, classId);
        List<AttendanceSession> classSessions = sessions.findByClassIdOrderByOpenedAtAsc(classId);
        if (!classSessions.isEmpty()) {
            List<Long> sessionIds = classSessions.stream().map(AttendanceSession::getId).toList();
            records.deleteBySessionIdIn(sessionIds);
            sessions.deleteAll(classSessions);
        }
        enrollments.deleteByClassId(classId);
        classes.delete(clazz);
    }

    @Transactional(readOnly = true)
    public ClassReport report(String teacherId, Long classId) {
        ReportData d = load(teacherId, classId);
        List<ReportSession> sessionViews = d.sessions().stream()
                .map(s -> new ReportSession(s.getId(), s.getTitle(), s.getOpenedAt(),
                        d.present().getOrDefault(s.getId(), Set.of()).size()))
                .toList();
        List<ReportRow> rows = new ArrayList<>();
        for (Enrollment e : d.roster()) {
            int attended = 0;
            int total = 0;
            for (AttendanceSession s : d.sessions()) {
                boolean present = d.present().getOrDefault(s.getId(), Set.of()).contains(e.getStudentId());
                if (applicable(e, s, present)) total++;
                if (present) attended++;
            }
            double rate = total == 0 ? 0 : Math.round(attended * 1000.0 / total) / 10.0;
            rows.add(new ReportRow(e.getStudentId(), d.names().getOrDefault(e.getStudentId(), ""),
                    attended, total, rate));
        }
        return new ClassReport(view(d.clazz(), names(List.of(teacherId)), d.roster().size()), sessionViews, rows);
    }

    /** Bảng điểm danh cả lớp: mỗi dòng một sinh viên, mỗi cột một buổi (CÓ / VẮNG / trống nếu chưa vào lớp). */
    @Transactional(readOnly = true)
    public String exportCsv(String teacherId, Long classId) {
        ReportData d = load(teacherId, classId);
        StringBuilder sb = new StringBuilder();
        sb.append(Csv.row("Lớp học phần", d.clazz().getCode() + " - " + d.clazz().getName()));

        List<String> header = new ArrayList<>(List.of("Mã SV", "Họ tên"));
        for (AttendanceSession s : d.sessions()) {
            header.add(s.getTitle() + " (" + COLUMN_TIME.format(s.getOpenedAt()) + ")");
        }
        header.addAll(List.of("Số buổi có mặt", "Tổng số buổi", "Tỷ lệ (%)"));
        sb.append(Csv.row(header.toArray(new String[0])));

        for (Enrollment e : d.roster()) {
            List<String> cells = new ArrayList<>();
            cells.add(e.getStudentId());
            cells.add(d.names().getOrDefault(e.getStudentId(), ""));
            int attended = 0;
            int total = 0;
            for (AttendanceSession s : d.sessions()) {
                boolean present = d.present().getOrDefault(s.getId(), Set.of()).contains(e.getStudentId());
                if (applicable(e, s, present)) {
                    total++;
                    cells.add(present ? "CÓ" : "VẮNG");
                } else {
                    cells.add("");
                }
                if (present) attended++;
            }
            cells.add(String.valueOf(attended));
            cells.add(String.valueOf(total));
            cells.add(total == 0 ? "" : String.format(Locale.ROOT, "%.1f", attended * 100.0 / total));
            sb.append(Csv.row(cells.toArray(new String[0])));
        }
        return sb.toString();
    }

    /** Bảng điểm danh cả lớp dạng Excel: mỗi cột một buổi, CÓ tô xanh, VẮNG tô đỏ, kèm tỷ lệ chuyên cần. */
    @Transactional(readOnly = true)
    public byte[] exportXlsx(String teacherId, Long classId) {
        ReportData d = load(teacherId, classId);
        int sessionCount = d.sessions().size();
        int[] widths = new int[2 + sessionCount + 3 + 1];
        widths[0] = 6;
        widths[1] = 14;
        widths[2] = 28;
        for (int i = 0; i < sessionCount; i++) widths[3 + i] = 16;
        widths[3 + sessionCount] = 12;
        widths[4 + sessionCount] = 12;
        widths[5 + sessionCount] = 12;
        widths = java.util.Arrays.copyOf(widths, 6 + sessionCount);

        Xlsx x = new Xlsx("Bảng điểm danh").widths(widths);
        x.row(Style.TITLE, "BẢNG ĐIỂM DANH LỚP HỌC PHẦN");
        x.row(Style.NORMAL, "Lớp học phần: " + d.clazz().getCode() + " - " + d.clazz().getName());
        if (d.clazz().getSemester() != null && !d.clazz().getSemester().isBlank()) {
            x.row(Style.NORMAL, "Học kỳ: " + d.clazz().getSemester());
        }
        x.row(Style.NORMAL, "Sĩ số: " + d.roster().size() + " - Số buổi: " + sessionCount);
        x.blank();

        List<Object> header = new ArrayList<>(List.of("STT", "Mã SV", "Họ tên"));
        for (AttendanceSession s : d.sessions()) {
            header.add(s.getTitle() + "\n" + COLUMN_TIME.format(s.getOpenedAt()));
        }
        header.addAll(List.of("Có mặt", "Tổng buổi", "Tỷ lệ (%)"));
        x.row(Style.HEADER, header.toArray());

        int n = 0;
        for (Enrollment e : d.roster()) {
            n++;
            List<Object> cells = new ArrayList<>();
            List<Style> styles = new ArrayList<>();
            cells.add(n);
            cells.add(e.getStudentId());
            cells.add(d.names().getOrDefault(e.getStudentId(), ""));
            styles.addAll(List.of(Style.CELL, Style.CELL, Style.CELL));
            int attended = 0;
            int total = 0;
            for (AttendanceSession s : d.sessions()) {
                boolean present = d.present().getOrDefault(s.getId(), Set.of()).contains(e.getStudentId());
                if (applicable(e, s, present)) {
                    total++;
                    cells.add(present ? "CÓ" : "VẮNG");
                    styles.add(present ? Style.PRESENT : Style.ABSENT);
                } else {
                    cells.add("");
                    styles.add(Style.CELL);
                }
                if (present) attended++;
            }
            cells.add(attended);
            cells.add(total);
            cells.add(total == 0 ? "" : Math.round(attended * 1000.0 / total) / 10.0);
            styles.addAll(List.of(Style.CELL, Style.CELL, Style.CELL));
            x.row(styles.toArray(new Style[0]), cells.toArray());
        }
        return x.build();
    }

    // ---------------- Nội bộ ----------------

    private record ReportData(CourseClass clazz, List<AttendanceSession> sessions, List<Enrollment> roster,
                              Map<Long, Set<String>> present, Map<String, String> names) {}

    private ReportData load(String teacherId, Long classId) {
        CourseClass clazz = owned(teacherId, classId);
        List<AttendanceSession> classSessions = sessions.findByClassIdOrderByOpenedAtAsc(classId);
        List<Enrollment> roster = enrollments.findByClassIdOrderByStudentIdAsc(classId);

        Map<Long, Set<String>> present = new HashMap<>();
        if (!classSessions.isEmpty()) {
            List<Long> sessionIds = classSessions.stream().map(AttendanceSession::getId).toList();
            for (AttendanceRecord r : records.findBySessionIdIn(sessionIds)) {
                present.computeIfAbsent(r.getSessionId(), k -> new HashSet<>()).add(r.getStudentId());
            }
        }
        Set<String> studentIds = new HashSet<>();
        roster.forEach(e -> studentIds.add(e.getStudentId()));
        return new ReportData(clazz, classSessions, roster, present, names(studentIds));
    }

    /**
     * Một buổi chỉ tính vào chuyên cần của sinh viên nếu họ đã có trong lớp khi buổi mở,
     * hoặc đã có mặt (kể cả điểm danh thủ công). Nhờ vậy người được thêm vào lớp muộn không bị tính vắng các buổi trước đó.
     */
    private static boolean applicable(Enrollment e, AttendanceSession s, boolean present) {
        return present || !e.getEnrolledAt().isAfter(s.getOpenedAt());
    }

    private CourseClass owned(String teacherId, Long classId) {
        CourseClass clazz = classes.findById(classId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CLASS_NOT_FOUND", "Không tìm thấy lớp học phần"));
        if (!clazz.getTeacherId().equals(teacherId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_OWNER", "Đây không phải lớp học phần của bạn");
        }
        return clazz;
    }

    private ClassView view(CourseClass c, Map<String, String> names, long studentCount) {
        return new ClassView(c.getId(), c.getCode(), c.getName(), c.getSemester(),
                c.getDayOfWeek(), c.getShift(), c.getRoom(),
                names.getOrDefault(c.getTeacherId(), ""), studentCount);
    }

    private Map<String, String> names(Collection<String> ids) {
        Map<String, String> map = new HashMap<>();
        for (User u : users.findAllById(ids)) {
            map.put(u.getUserId(), u.getName());
        }
        return map;
    }
}
