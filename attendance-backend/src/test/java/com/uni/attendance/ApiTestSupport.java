package com.uni.attendance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tiện ích dùng chung cho các kiểm thử tích hợp (MockMvc + H2 trong bộ nhớ). */
abstract class ApiTestSupport {
    static final String PASSWORD = "Passw0rd!x";
    static final String INVITE = "TEST-INVITE";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    // ---------------- HTTP ----------------

    ResultActions send(MockHttpServletRequestBuilder b, String token, Object body) throws Exception {
        if (body != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        }
        if (token != null) {
            b.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mvc.perform(b);
    }

    ResultActions post(String url, String token, Object body) throws Exception {
        return send(MockMvcRequestBuilders.post(url), token, body);
    }

    ResultActions put(String url, String token, Object body) throws Exception {
        return send(MockMvcRequestBuilders.put(url), token, body);
    }

    ResultActions get(String url, String token) throws Exception {
        return send(MockMvcRequestBuilders.get(url), token, null);
    }

    ResultActions delete(String url, String token) throws Exception {
        return send(MockMvcRequestBuilders.delete(url), token, null);
    }

    JsonNode json(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    String text(ResultActions result) throws Exception {
        return new String(result.andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    // ---------------- Dữ liệu mẫu ----------------

    /** Đăng ký và trả về token. role là STUDENT hoặc TEACHER. */
    String register(String id, String name, String role) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("userId", id);
        body.put("name", name);
        body.put("password", PASSWORD);
        body.put("role", role);
        if (role.equals("TEACHER")) {
            body.put("inviteCode", INVITE);
        }
        return json(post("/api/auth/register", null, body).andExpect(status().isCreated())).get("token").asText();
    }

    long createClass(String teacherToken, String code) throws Exception {
        return json(post("/api/classes", teacherToken,
                Map.of("code", code, "name", "Lớp " + code, "semester", "HK1 2026-2027"))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    void enroll(String teacherToken, long classId, String... studentIds) throws Exception {
        post("/api/classes/" + classId + "/students", teacherToken,
                Map.of("studentIds", Arrays.asList(studentIds))).andExpect(status().isOk());
    }

    long openSession(String teacherToken, long classId, Map<String, Object> extra) throws Exception {
        Map<String, Object> body = new HashMap<>(extra);
        body.put("classId", classId);
        return json(post("/api/sessions", teacherToken, body).andExpect(status().isCreated())).get("id").asLong();
    }

    ResultActions attend(String token, long sessionId, float[] embedding) throws Exception {
        return post("/api/attendance", token, Map.of("sessionId", sessionId, "embedding", embedding));
    }

    // ---------------- Vector khuôn mặt giả lập ----------------

    static float[] face(long seed) {
        Random r = new Random(seed);
        float[] v = new float[192];
        for (int i = 0; i < v.length; i++) v[i] = (float) r.nextGaussian();
        return v;
    }

    /** Cùng một người nhưng mỗi lần chụp lệch nhẹ (cosine khoảng 0.99). */
    static float[] sameFaceNoisy(float[] base, long seed) {
        Random r = new Random(seed);
        float[] v = new float[base.length];
        for (int i = 0; i < v.length; i++) v[i] = base[i] + 0.1f * (float) r.nextGaussian();
        return v;
    }
}
