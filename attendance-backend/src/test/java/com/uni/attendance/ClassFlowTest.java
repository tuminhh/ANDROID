package com.uni.attendance;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Lớp học phần, danh sách sinh viên, danh sách vắng và báo cáo chuyên cần. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClassFlowTest extends ApiTestSupport {

    private static List<String> strings(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }

    @Test
    void classCodeIsUniqueAndOwnerOnly() throws Exception {
        String t1 = register("T_CLS1", "Cô Một", "TEACHER");
        String t2 = register("T_CLS2", "Thầy Hai", "TEACHER");
        String student = register("SV_CLS1", "Sinh viên", "STUDENT");
        long id = createClass(t1, "CLS_UNIQUE");

        // Mã lớp không phân biệt hoa thường
        post("/api/classes", t2, Map.of("code", "cls_unique", "name", "Trùng mã"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLASS_EXISTS"));
        post("/api/classes", student, Map.of("code", "CLS_BYSTUDENT", "name", "Lớp lậu"))
                .andExpect(status().isForbidden());
        post("/api/classes", t1, Map.of("code", "co dau cach", "name", "Sai định dạng"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // Giảng viên khác không được đụng tới lớp
        get("/api/classes/" + id, t2).andExpect(status().isForbidden());
        get("/api/classes/" + id + "/report", t2).andExpect(status().isForbidden());
        get("/api/classes/" + id + "/export", t2).andExpect(status().isForbidden());
        get("/api/classes/" + id + "/export.xlsx", t2).andExpect(status().isForbidden());
        post("/api/classes/" + id + "/students", t2, Map.of("studentIds", List.of("SV_CLS1")))
                .andExpect(status().isForbidden());
        get("/api/classes/99999999", t1).andExpect(status().isNotFound());

        JsonNode mine = json(get("/api/classes/mine", t1).andExpect(status().isOk()));
        assertThat(mine.size()).isEqualTo(1);
        assertThat(mine.get(0).get("code").asText()).isEqualTo("CLS_UNIQUE");
        assertThat(mine.get(0).get("studentCount").asLong()).isZero();
        assertThat(json(get("/api/classes/mine", t2)).size()).isZero();
    }

    @Test
    void rosterBulkAddReportsEachCase() throws Exception {
        String teacher = register("T_ROSTER", "Cô Roster", "TEACHER");
        String s1 = register("SV_R1", "Sinh viên Một", "STUDENT");
        register("SV_R2", "Sinh viên Hai", "STUDENT");
        long id = createClass(teacher, "CLS_ROSTER");

        // Chữ thường, trùng lặp, mã của giảng viên và mã không tồn tại đều được xử lý riêng từng mã
        JsonNode first = json(post("/api/classes/" + id + "/students", teacher,
                Map.of("studentIds", List.of("sv_r1", "SV_R2", "SV_R2", "T_ROSTER", "SV_NONE")))
                .andExpect(status().isOk()));
        assertThat(strings(first.get("added"))).containsExactly("SV_R1", "SV_R2");
        assertThat(strings(first.get("alreadyEnrolled"))).isEmpty();
        assertThat(strings(first.get("notFound"))).containsExactly("T_ROSTER", "SV_NONE");

        JsonNode second = json(post("/api/classes/" + id + "/students", teacher,
                Map.of("studentIds", List.of("SV_R1", "SV_CHUA_DANG_KY")))
                .andExpect(status().isOk()));
        assertThat(strings(second.get("added"))).isEmpty();
        assertThat(strings(second.get("alreadyEnrolled"))).containsExactly("SV_R1");
        assertThat(strings(second.get("notFound"))).containsExactly("SV_CHUA_DANG_KY");

        post("/api/classes/" + id + "/students", teacher, Map.of("studentIds", List.of()))
                .andExpect(status().isBadRequest());

        JsonNode detail = json(get("/api/classes/" + id, teacher).andExpect(status().isOk()));
        assertThat(detail.get("info").get("studentCount").asLong()).isEqualTo(2);
        assertThat(detail.get("students").get(0).get("studentId").asText()).isEqualTo("SV_R1");
        assertThat(detail.get("students").get(0).get("name").asText()).isEqualTo("Sinh viên Một");

        // Sinh viên thấy lớp của mình; vai trò khác thì không dùng được
        JsonNode enrolled = json(get("/api/classes/enrolled", s1).andExpect(status().isOk()));
        assertThat(enrolled.size()).isEqualTo(1);
        assertThat(enrolled.get(0).get("code").asText()).isEqualTo("CLS_ROSTER");
        assertThat(enrolled.get(0).get("teacherName").asText()).isEqualTo("Cô Roster");
        get("/api/classes/enrolled", teacher).andExpect(status().isForbidden());
        get("/api/classes/mine", s1).andExpect(status().isForbidden());

        delete("/api/classes/" + id + "/students/SV_R2", teacher).andExpect(status().isNoContent());
        delete("/api/classes/" + id + "/students/SV_R2", teacher)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_IN_CLASS"));
        assertThat(json(get("/api/classes/" + id, teacher)).get("students").size()).isEqualTo(1);
    }

    @Test
    void onlyEnrolledStudentsSeeAndJoinSessions() throws Exception {
        String teacher = register("T_ENR", "Cô Lớp", "TEACHER");
        String otherTeacher = register("T_ENR2", "Thầy Khác", "TEACHER");
        String member = register("SV_ENR_IN", "Thành viên", "STUDENT");
        String outsider = register("SV_ENR_OUT", "Người ngoài", "STUDENT");
        long classId = createClass(teacher, "CLS_ENR");
        long otherClassId = createClass(otherTeacher, "CLS_ENR_OTHER");
        enroll(teacher, classId, "SV_ENR_IN");
        put("/api/face", member, Map.of("embedding", face(41))).andExpect(status().isNoContent());
        put("/api/face", outsider, Map.of("embedding", face(42))).andExpect(status().isNoContent());

        long sid = openSession(teacher, classId, Map.of("title", "Buổi của lớp ENR"));

        // Chỉ thành viên mới thấy buổi
        boolean memberSees = false;
        for (JsonNode node : json(get("/api/sessions/active", member).andExpect(status().isOk()))) {
            if (node.get("id").asLong() == sid) memberSees = true;
        }
        assertThat(memberSees).isTrue();
        assertThat(json(get("/api/sessions/active", outsider)).size()).as("người ngoài lớp không thấy buổi nào").isZero();

        // Người ngoài lớp không điểm danh được, giảng viên cũng không đánh dấu thủ công cho họ
        attend(outsider, sid, face(42)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ENROLLED"));
        post("/api/sessions/" + sid + "/records", teacher, Map.of("studentId", "SV_ENR_OUT"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOT_ENROLLED"));
        attend(member, sid, sameFaceNoisy(face(41), 1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(true));

        // Chỉ mở được buổi cho lớp của chính mình
        post("/api/sessions", teacher, Map.of("classId", otherClassId, "title", "Lớp người khác"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NOT_OWNER"));
        post("/api/sessions", teacher, Map.of("classId", 99999999, "title", "Lớp không có"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"));
        post("/api/sessions", teacher, Map.of("title", "Thiếu lớp"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void absentListAndAttendanceReport() throws Exception {
        String teacher = register("T_REP", "Cô Báo Cáo", "TEACHER");
        register("SV_REP1", "Học Chăm", "STUDENT");
        register("SV_REP2", "Học Vừa", "STUDENT");
        register("SV_REP3", "Hay Vắng", "STUDENT");
        register("SV_REP4", "Vào Muộn", "STUDENT");
        long classId = createClass(teacher, "CLS_REPORT");
        enroll(teacher, classId, "SV_REP1", "SV_REP2", "SV_REP3");

        long first = openSession(teacher, classId, Map.of("title", "Buổi 1"));
        post("/api/sessions/" + first + "/records", teacher, Map.of("studentId", "SV_REP1")).andExpect(status().isCreated());
        post("/api/sessions/" + first + "/records", teacher, Map.of("studentId", "SV_REP2")).andExpect(status().isCreated());

        long second = openSession(teacher, classId, Map.of("title", "Buổi 2")); // tự đóng buổi 1
        post("/api/sessions/" + second + "/records", teacher, Map.of("studentId", "SV_REP1")).andExpect(status().isCreated());

        // Thêm vào lớp sau khi cả hai buổi đã mở: không bị tính vắng các buổi đó
        enroll(teacher, classId, "SV_REP4");

        JsonNode records = json(get("/api/sessions/" + first + "/records", teacher).andExpect(status().isOk()));
        assertThat(records.get("records").size()).isEqualTo(2);
        assertThat(records.get("absent").size()).isEqualTo(1);
        assertThat(records.get("absent").get(0).get("studentId").asText()).isEqualTo("SV_REP3");
        assertThat(records.get("rosterSize").asInt()).isEqualTo(4);

        JsonNode report = json(get("/api/classes/" + classId + "/report", teacher).andExpect(status().isOk()));
        assertThat(report.get("sessions").size()).isEqualTo(2);
        assertThat(report.get("sessions").get(0).get("title").asText()).isEqualTo("Buổi 1");
        assertThat(report.get("sessions").get(0).get("presentCount").asLong()).isEqualTo(2);
        assertThat(report.get("sessions").get(1).get("presentCount").asLong()).isEqualTo(1);

        Map<String, JsonNode> rows = new HashMap<>();
        report.get("rows").forEach(r -> rows.put(r.get("studentId").asText(), r));
        assertThat(rows).containsOnlyKeys("SV_REP1", "SV_REP2", "SV_REP3", "SV_REP4");
        assertThat(rows.get("SV_REP1").get("attended").asInt()).isEqualTo(2);
        assertThat(rows.get("SV_REP1").get("total").asInt()).isEqualTo(2);
        assertThat(rows.get("SV_REP1").get("ratePercent").asDouble()).isEqualTo(100.0);
        assertThat(rows.get("SV_REP2").get("ratePercent").asDouble()).isEqualTo(50.0);
        assertThat(rows.get("SV_REP3").get("attended").asInt()).isZero();
        assertThat(rows.get("SV_REP3").get("total").asInt()).isEqualTo(2);
        assertThat(rows.get("SV_REP4").get("total").asInt()).as("vào lớp muộn không bị tính vắng").isZero();

        String csv = text(get("/api/classes/" + classId + "/export", teacher).andExpect(status().isOk()));
        assertThat(csv).contains("CLS_REPORT").contains("Buổi 1").contains("Buổi 2")
                .contains("SV_REP3").contains("CÓ").contains("VẮNG");

        String sessionCsv = text(get("/api/sessions/" + first + "/export", teacher).andExpect(status().isOk()));
        assertThat(sessionCsv).contains("SV_REP3").contains("VẮNG");

        // Excel: là file zip (bắt đầu bằng "PK"), có tên sinh viên và chỉ giảng viên của lớp mới tải được
        byte[] classXlsx = get("/api/classes/" + classId + "/export.xlsx", teacher).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(classXlsx[0]).isEqualTo((byte) 'P');
        assertThat(classXlsx[1]).isEqualTo((byte) 'K');
        assertThat(zipText(classXlsx)).contains("SV_REP3").contains("VẮNG").contains("CLS_REPORT");

        byte[] sessionXlsx = get("/api/sessions/" + first + "/export.xlsx", teacher).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(zipText(sessionXlsx)).contains("SV_REP3").contains("Vắng");
    }

    /** Gộp nội dung văn bản của mọi phần trong file xlsx để kiểm tra nhanh. */
    private static String zipText(byte[] xlsx) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(xlsx))) {
            while (zip.getNextEntry() != null) {
                sb.append(new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        return sb.toString();
    }
}
