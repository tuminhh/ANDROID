package com.uni.attendance.service;

import com.uni.attendance.config.AppProperties;
import com.uni.attendance.domain.AttendanceAttempt;
import com.uni.attendance.domain.AttendanceRecord;
import com.uni.attendance.domain.AttendanceSession;
import com.uni.attendance.domain.FaceTemplate;
import com.uni.attendance.repo.AttemptRepository;
import com.uni.attendance.repo.EnrollmentRepository;
import com.uni.attendance.repo.FaceTemplateRepository;
import com.uni.attendance.repo.RecordRepository;
import com.uni.attendance.repo.SessionRepository;
import com.uni.attendance.util.Embeddings;
import com.uni.attendance.util.Geo;
import com.uni.attendance.web.ApiException;
import com.uni.attendance.web.Dtos.AttendanceRequest;
import com.uni.attendance.web.Dtos.AttendanceResult;
import com.uni.attendance.web.Dtos.HistoryView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AttendanceService {
    private final SessionRepository sessions;
    private final RecordRepository records;
    private final AttemptRepository attempts;
    private final FaceTemplateRepository faces;
    private final EnrollmentRepository enrollments;
    private final FaceService faceService;
    private final AppProperties props;

    public AttendanceService(SessionRepository sessions, RecordRepository records, AttemptRepository attempts,
                             FaceTemplateRepository faces, EnrollmentRepository enrollments,
                             FaceService faceService, AppProperties props) {
        this.sessions = sessions;
        this.records = records;
        this.attempts = attempts;
        this.faces = faces;
        this.enrollments = enrollments;
        this.faceService = faceService;
        this.props = props;
    }

    /**
     * Điểm danh bằng vector khuôn mặt do app gửi lên.
     * Lỗi quy tắc (đóng buổi, trùng, ngoài phạm vi...) trả về qua ApiException;
     * sai khuôn mặt trả về HTTP 200 với matched=false để lần thử vẫn được ghi nhật ký.
     */
    @Transactional
    public AttendanceResult submit(String studentId, AttendanceRequest req) {
        Instant now = Instant.now();

        AttendanceSession session = sessions.findById(req.sessionId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Không tìm thấy buổi điểm danh"));
        if (!session.isActiveAt(now)) {
            throw new ApiException(HttpStatus.GONE, "SESSION_CLOSED", "Buổi điểm danh đã đóng hoặc hết hạn");
        }
        if (session.getClassId() == null
                || !enrollments.existsByClassIdAndStudentId(session.getClassId(), studentId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_ENROLLED",
                    "Bạn không có tên trong danh sách lớp học phần của buổi này");
        }
        if (records.existsBySessionIdAndStudentId(session.getId(), studentId)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_MARKED", "Bạn đã điểm danh buổi này rồi");
        }
        FaceTemplate template = faces.findById(studentId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "FACE_NOT_ENROLLED", "Bạn chưa đăng ký khuôn mặt"));

        int maxFailed = props.face().maxFailedAttempts();
        long failures = attempts.countBySessionIdAndStudentIdAndSuccessFalse(session.getId(), studentId);
        if (failures >= maxFailed) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "ATTEMPTS_EXHAUSTED",
                    "Bạn đã hết lượt thử trong buổi này, hãy liên hệ giảng viên");
        }

        Double distance = null;
        if (session.hasLocation()) {
            if (req.latitude() == null || req.longitude() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "LOCATION_REQUIRED",
                        "Buổi này yêu cầu vị trí của bạn");
            }
            distance = Geo.distanceMeters(session.getLatitude(), session.getLongitude(),
                    req.latitude(), req.longitude());
            if (distance > session.getRadiusMeters()) {
                throw new ApiException(HttpStatus.FORBIDDEN, "OUT_OF_RANGE",
                        "Bạn đang ở ngoài phạm vi điểm danh của buổi học");
            }
        }

        float[] probe = faceService.prepare(req.embedding());
        double similarity = Embeddings.cosine(Embeddings.parse(template.getEmbedding()), probe);
        boolean matched = similarity >= props.face().threshold();
        attempts.save(new AttendanceAttempt(session.getId(), studentId, now, similarity, matched));

        if (matched) {
            records.save(new AttendanceRecord(session.getId(), studentId, now, similarity, distance,
                    AttendanceRecord.Method.FACE));
            return new AttendanceResult(true, round4(similarity), "Điểm danh thành công", null);
        }
        int left = (int) Math.max(0, maxFailed - failures - 1);
        String message = left > 0
                ? "Khuôn mặt không khớp. Bạn còn " + left + " lần thử"
                : "Khuôn mặt không khớp. Bạn đã hết lượt thử, hãy liên hệ giảng viên";
        return new AttendanceResult(false, null, message, left);
    }

    @Transactional(readOnly = true)
    public List<HistoryView> history(String studentId) {
        List<AttendanceRecord> list = records.findByStudentIdOrderByCheckedAtDesc(studentId);
        Map<Long, String> titles = new HashMap<>();
        sessions.findAllById(list.stream().map(AttendanceRecord::getSessionId).toList())
                .forEach(s -> titles.put(s.getId(), s.getTitle()));
        return list.stream()
                .map(r -> new HistoryView(r.getSessionId(), titles.getOrDefault(r.getSessionId(), ""),
                        r.getCheckedAt(), r.getSimilarity(), r.getMethod().name()))
                .toList();
    }

    private static double round4(double v) {
        return Math.round(v * 10_000d) / 10_000d;
    }
}
