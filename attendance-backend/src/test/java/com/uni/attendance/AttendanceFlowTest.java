package com.uni.attendance;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Xác thực, phân quyền và luồng điểm danh (mỗi buổi thuộc một lớp học phần có danh sách sinh viên). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttendanceFlowTest extends ApiTestSupport {

    @Test
    void registrationRulesAreEnforced() throws Exception {
        // Giảng viên cần mã mời đúng
        post("/api/auth/register", null, Map.of("userId", "T_REG1", "name", "Thầy A",
                "password", PASSWORD, "role", "TEACHER"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("INVALID_INVITE_CODE"));
        post("/api/auth/register", null, Map.of("userId", "T_REG1", "name", "Thầy A",
                "password", PASSWORD, "role", "TEACHER", "inviteCode", "sai"))
                .andExpect(status().isForbidden());
        register("T_REG1", "Thầy A", "TEACHER");

        // Trùng mã (không phân biệt hoa thường)
        post("/api/auth/register", null, Map.of("userId", "t_reg1", "name", "Người khác",
                "password", PASSWORD, "role", "STUDENT"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USER_EXISTS"));

        // Mật khẩu ngắn
        post("/api/auth/register", null, Map.of("userId", "SV_REG2", "name", "Sinh viên",
                "password", "123", "role", "STUDENT"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void authenticationAndRolesAreEnforced() throws Exception {
        String student = register("SV_AUTH1", "Sinh viên", "STUDENT");
        String teacher = register("T_AUTH1", "Giảng viên", "TEACHER");

        get("/api/sessions/active", null).andExpect(status().isUnauthorized());
        get("/api/sessions/active", "token-rac").andExpect(status().isUnauthorized());
        // Body hợp lệ để chắc chắn lỗi đến từ phân quyền chứ không phải kiểm tra dữ liệu
        post("/api/sessions", student, Map.of("classId", 1, "title", "Buổi lậu")).andExpect(status().isForbidden());
        get("/api/sessions/active", teacher).andExpect(status().isForbidden());

        get("/api/me", student).andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("SV_AUTH1"))
                .andExpect(jsonPath("$.faceEnrolled").value(false))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void loginLocksAfterRepeatedFailures() throws Exception {
        register("SV_LOCK1", "Sinh viên", "STUDENT");
        Map<String, String> wrong = Map.of("userId", "SV_LOCK1", "password", "sai-mat-khau");
        for (int i = 0; i < 3; i++) {
            post("/api/auth/login", null, wrong).andExpect(status().isUnauthorized());
        }
        // Đã bị khóa: ngay cả mật khẩu đúng cũng bị từ chối tạm thời
        post("/api/auth/login", null, Map.of("userId", "SV_LOCK1", "password", PASSWORD))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void fullAttendanceFlow() throws Exception {
        String teacher = register("T_FLOW", "Cô Lan", "TEACHER");
        String otherTeacher = register("T_FLOW2", "Thầy Bình", "TEACHER");
        String s1 = register("SV_FLOW1", "Sinh Viên Một", "STUDENT");
        String s2 = register("SV_FLOW2", "Sinh Viên Hai", "STUDENT");
        long classId = createClass(teacher, "CL_FLOW");
        enroll(teacher, classId, "SV_FLOW1", "SV_FLOW2");

        float[] face1 = face(1);
        put("/api/face", s1, Map.of("embedding", face1)).andExpect(status().isNoContent());
        get("/api/face/status", s1).andExpect(jsonPath("$.enrolled").value(true));

        long sid = openSession(teacher, classId, Map.of("title", "Java - Buổi 1", "durationMinutes", 30));
        JsonNode listed = null;
        for (JsonNode node : json(get("/api/sessions/active", s1).andExpect(status().isOk()))) {
            if (node.get("id").asLong() == sid) listed = node;
        }
        assertThat(listed).as("buổi vừa mở phải nằm trong danh sách đang mở").isNotNull();
        assertThat(listed.get("title").asText()).isEqualTo("Java - Buổi 1");
        assertThat(listed.get("classCode").asText()).isEqualTo("CL_FLOW");
        assertThat(listed.has("latitude")).as("không lộ tọa độ cho sinh viên").isFalse();

        // Sinh viên chưa đăng ký mặt thì không điểm danh được
        attend(s2, sid, face(2)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FACE_NOT_ENROLLED"));

        // Điểm danh đúng người
        attend(s1, sid, sameFaceNoisy(face1, 11)).andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(true))
                .andExpect(jsonPath("$.similarity").isNumber());
        // Điểm danh lần hai bị từ chối
        attend(s1, sid, sameFaceNoisy(face1, 12)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_MARKED"));

        get("/api/attendance/history", s1).andExpect(jsonPath("$[0].sessionTitle").value("Java - Buổi 1"));

        // Giảng viên của buổi xem được cả người có mặt lẫn người vắng; giảng viên khác thì không
        get("/api/sessions/" + sid + "/records", teacher).andExpect(status().isOk())
                .andExpect(jsonPath("$.records.length()").value(1))
                .andExpect(jsonPath("$.records[0].studentId").value("SV_FLOW1"))
                .andExpect(jsonPath("$.absent.length()").value(1))
                .andExpect(jsonPath("$.absent[0].studentId").value("SV_FLOW2"))
                .andExpect(jsonPath("$.rosterSize").value(2));
        get("/api/sessions/" + sid + "/records", otherTeacher).andExpect(status().isForbidden());
        get("/api/sessions/" + sid + "/records", s1).andExpect(status().isForbidden());

        String csv = text(get("/api/sessions/" + sid + "/export", teacher).andExpect(status().isOk()));
        assertThat(csv).contains("SV_FLOW1").contains("Sinh Viên Một").contains("SV_FLOW2").contains("VẮNG");
    }

    @Test
    void impostorIsRejectedAndLockedOut() throws Exception {
        String teacher = register("T_FAKE", "Cô Hoa", "TEACHER");
        String victim = register("SV_VICTIM", "Nạn nhân", "STUDENT");
        String attacker = register("SV_ATTACKER", "Kẻ mạo danh", "STUDENT");
        long classId = createClass(teacher, "CL_FAKE");
        enroll(teacher, classId, "SV_VICTIM", "SV_ATTACKER");
        put("/api/face", victim, Map.of("embedding", face(100))).andExpect(status().isNoContent());
        put("/api/face", attacker, Map.of("embedding", face(200))).andExpect(status().isNoContent());
        long sid = openSession(teacher, classId, Map.of("title", "Buổi thử gian lận"));

        // Kẻ mạo danh dùng khuôn mặt khác: không khớp, không lộ độ tương đồng
        attend(attacker, sid, face(999)).andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(false))
                .andExpect(jsonPath("$.similarity").doesNotExist())
                .andExpect(jsonPath("$.attemptsLeft").value(4));
        for (int i = 0; i < 4; i++) {
            attend(attacker, sid, face(1000 + i)).andExpect(jsonPath("$.matched").value(false));
        }
        // Hết 5 lượt thì bị chặn, kể cả khi lần sau gửi đúng khuôn mặt
        attend(attacker, sid, sameFaceNoisy(face(200), 5)).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("ATTEMPTS_EXHAUSTED"));

        // Giảng viên có thể xác nhận thủ công cho sinh viên bị khóa
        post("/api/sessions/" + sid + "/records", teacher, Map.of("studentId", "sv_attacker"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.method").value("MANUAL"));
    }

    @Test
    void locationIsEnforcedWhenRequired() throws Exception {
        String teacher = register("T_GEO", "Cô Mai", "TEACHER");
        String student = register("SV_GEO1", "Sinh viên", "STUDENT");
        long classId = createClass(teacher, "CL_GEO");
        enroll(teacher, classId, "SV_GEO1");
        float[] face = face(7);
        put("/api/face", student, Map.of("embedding", face)).andExpect(status().isNoContent());

        Map<String, Object> open = new HashMap<>();
        open.put("title", "Có vị trí");
        open.put("latitude", 21.0285);
        open.put("longitude", 105.8542);
        open.put("radiusMeters", 100);
        long sid = openSession(teacher, classId, open);

        attend(student, sid, sameFaceNoisy(face, 1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LOCATION_REQUIRED"));

        Map<String, Object> far = new HashMap<>();
        far.put("sessionId", sid);
        far.put("embedding", sameFaceNoisy(face, 2));
        far.put("latitude", 21.05);
        far.put("longitude", 105.90);
        post("/api/attendance", student, far).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("OUT_OF_RANGE"));

        Map<String, Object> near = new HashMap<>(far);
        near.put("embedding", sameFaceNoisy(face, 3));
        near.put("latitude", 21.0286);
        near.put("longitude", 105.8543);
        post("/api/attendance", student, near).andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(true));
    }

    @Test
    void closedSessionRejectsAttendance() throws Exception {
        String teacher = register("T_CLOSE", "Thầy Đông", "TEACHER");
        String student = register("SV_CLOSE1", "Sinh viên", "STUDENT");
        long classId = createClass(teacher, "CL_CLOSE");
        enroll(teacher, classId, "SV_CLOSE1");
        float[] face = face(8);
        put("/api/face", student, Map.of("embedding", face)).andExpect(status().isNoContent());

        long sid = openSession(teacher, classId, Map.of("title", "Sắp đóng"));
        post("/api/sessions/" + sid + "/close", teacher, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        attend(student, sid, sameFaceNoisy(face, 1)).andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("SESSION_CLOSED"));
        for (JsonNode node : json(get("/api/sessions/active", student).andExpect(status().isOk()))) {
            assertThat(node.get("id").asLong()).as("buổi đã đóng không còn trong danh sách").isNotEqualTo(sid);
        }
    }

    @Test
    void manualMarkCanBeAddedAndRemoved() throws Exception {
        String teacher = register("T_MAN", "Cô Thu", "TEACHER");
        String student = register("SV_MAN1", "Sinh viên", "STUDENT");
        long classId = createClass(teacher, "CL_MAN");
        enroll(teacher, classId, "SV_MAN1");
        long sid = openSession(teacher, classId, Map.of("title", "Điểm danh tay"));

        post("/api/sessions/" + sid + "/records", student, Map.of("studentId", "SV_MAN1"))
                .andExpect(status().isForbidden());
        post("/api/sessions/" + sid + "/records", teacher, Map.of("studentId", "KHONG_CO"))
                .andExpect(status().isNotFound());
        post("/api/sessions/" + sid + "/records", teacher, Map.of("studentId", "SV_MAN1"))
                .andExpect(status().isCreated());
        post("/api/sessions/" + sid + "/records", teacher, Map.of("studentId", "SV_MAN1"))
                .andExpect(status().isConflict());

        delete("/api/sessions/" + sid + "/records/SV_MAN1", teacher).andExpect(status().isNoContent());
        delete("/api/sessions/" + sid + "/records/SV_MAN1", teacher).andExpect(status().isNotFound());
    }

    @Test
    void reEnrollingFaceRequiresPassword() throws Exception {
        String student = register("SV_REENROLL", "Sinh viên", "STUDENT");
        put("/api/face", student, Map.of("embedding", face(30))).andExpect(status().isNoContent());

        put("/api/face", student, Map.of("embedding", face(31)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_REQUIRED"));
        put("/api/face", student, Map.of("embedding", face(31), "password", "sai-roi"))
                .andExpect(status().isForbidden());
        put("/api/face", student, Map.of("embedding", face(31), "password", PASSWORD))
                .andExpect(status().isNoContent());

        // Vector sai số chiều bị từ chối
        put("/api/face", student, Map.of("embedding", new float[32], "password", PASSWORD))
                .andExpect(status().isBadRequest());
    }
}
