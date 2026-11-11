package com.uni.attendance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uni.attendance.config.AppProperties;
import com.uni.attendance.web.Dtos.TimetableItemDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Tích hợp Google Gemini API (Interactions API / Multimodal Vision với mô hình gemini-3.8-flash)
 * để tự động phân tích và trích xuất dữ liệu thời khóa biểu từ ảnh chụp màn hình.
 */
@Service
public class GeminiService {
    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);

    private final AppProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public GeminiService(AppProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Gửi ảnh chụp màn hình lên Gemini 3.8 Flash để trích xuất danh sách môn học.
     */
    public List<TimetableItemDto> parseTimetableFromImage(String imageBase64) {
        String apiKey = properties.gemini() != null ? properties.gemini().apiKey() : null;
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("GEMINI_API_KEY");
        }

        if (apiKey != null && !apiKey.isBlank() && imageBase64 != null && !imageBase64.isBlank()) {
            try {
                List<TimetableItemDto> result = callGeminiInteractionsApi(apiKey, imageBase64);
                if (result != null && !result.isEmpty()) {
                    return result;
                }
            } catch (Exception e) {
                log.warn("Lỗi khi gọi Gemini Interactions API: {}. Đang thử fallback...", e.getMessage());
                try {
                    List<TimetableItemDto> fallbackResult = callGeminiGenerateContentApi(apiKey, imageBase64);
                    if (fallbackResult != null && !fallbackResult.isEmpty()) {
                        return fallbackResult;
                    }
                } catch (Exception ex) {
                    log.error("Cả 2 phương thức gọi Gemini API đều gặp lỗi: {}", ex.getMessage());
                }
            }
        }

        // Không tự động chèn dữ liệu mẫu giả lập
        return List.of();
    }

    private List<TimetableItemDto> callGeminiInteractionsApi(String apiKey, String imageBase64) throws Exception {
        String prompt = "Bạn là trợ lý AI thông minh phân tích thời khóa biểu sinh viên từ ảnh chụp màn hình. "
                + "Hãy trích xuất tất cả các môn học có trong ảnh thành mảng JSON. "
                + "Mỗi phần tử có các trường:\n"
                + "- dayOfWeek: số nguyên từ 2 đến 8 (2=Thứ 2, 3=Thứ 3, ..., 7=Thứ 7, 8=Chủ nhật)\n"
                + "- date: chuỗi ngày nếu có (ví dụ '09/11')\n"
                + "- shift: ca học (ví dụ 'Buổi sáng - 4,5,6')\n"
                + "- subjectName: tên môn học (ví dụ 'Đảm bảo chất lượng phần mềm')\n"
                + "- subjectCode: mã môn học (ví dụ 'SQA401')\n"
                + "- lecturer: tên giảng viên và số điện thoại/khoa (ví dụ 'Vũ Đình Minh (0912327850 - CNTT&TT)')\n"
                + "- room: phòng học và cơ sở (ví dụ '402 - A9 - Cơ sở 1 - Khu A')\n"
                + "- note: ghi chú nếu có.\n"
                + "Chỉ trả về JSON thuần túy, không có chữ dẫn dắt.";

        Map<String, Object> textInput = Map.of("type", "text", "text", prompt);
        Map<String, Object> imageInput = Map.of(
                "type", "image",
                "data", imageBase64,
                "mime_type", "image/jpeg"
        );

        Map<String, Object> requestBody = Map.of(
                "model", "gemini-3.8-flash",
                "input", List.of(textInput, imageInput),
                "response_mime_type", "application/json"
        );

        String jsonPayload = mapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/interactions"))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            JsonNode root = mapper.readTree(response.body());
            JsonNode steps = root.path("steps");
            for (JsonNode step : steps) {
                if ("model_output".equals(step.path("type").asText())) {
                    JsonNode content = step.path("content");
                    for (JsonNode c : content) {
                        String text = c.path("text").asText("");
                        if (!text.isBlank()) {
                            return parseJsonArray(text);
                        }
                    }
                }
            }
        }
        return null;
    }

    private List<TimetableItemDto> callGeminiGenerateContentApi(String apiKey, String imageBase64) throws Exception {
        String prompt = "Phân tích ảnh thời khóa biểu và trích xuất danh sách môn học thành mảng JSON với các trường: "
                + "dayOfWeek (2..8), date, shift, subjectName, subjectCode, lecturer, room, note. Chỉ trả về JSON.";

        Map<String, Object> textPart = Map.of("text", prompt);
        Map<String, Object> inlineData = Map.of("mime_type", "image/jpeg", "data", imageBase64);
        Map<String, Object> imagePart = Map.of("inline_data", inlineData);

        Map<String, Object> content = Map.of("parts", List.of(textPart, imagePart));
        Map<String, Object> generationConfig = Map.of("response_mime_type", "application/json");

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(content),
                "generationConfig", generationConfig
        );

        String jsonPayload = mapper.writeValueAsString(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent?key=" + apiKey))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            JsonNode root = mapper.readTree(response.body());
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                for (JsonNode p : parts) {
                    String text = p.path("text").asText("");
                    if (!text.isBlank()) {
                        return parseJsonArray(text);
                    }
                }
            }
        }
        return null;
    }

    private List<TimetableItemDto> parseJsonArray(String jsonText) {
        try {
            String clean = jsonText.trim();
            if (clean.startsWith("```json")) {
                clean = clean.substring(7);
            }
            if (clean.startsWith("```")) {
                clean = clean.substring(3);
            }
            if (clean.endsWith("```")) {
                clean = clean.substring(0, clean.length() - 3);
            }
            clean = clean.trim();
            return mapper.readValue(clean, new TypeReference<List<TimetableItemDto>>() {});
        } catch (Exception e) {
            log.warn("Không thể parse kết quả JSON từ Gemini: {}. Nội dung: {}", e.getMessage(), jsonText);
            return null;
        }
    }

    public static List<TimetableItemDto> getDefaultSampleItems() {
        return List.of();
    }
}
