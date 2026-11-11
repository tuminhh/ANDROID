package com.uni.attendance.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.uni.attendance.model.TimetableItem;
import com.uni.attendance.net.TokenStore;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Quản lý lưu trữ thời khóa biểu cục bộ (offline-first) bằng SharedPreferences và JSON.
 * Tách biệt dữ liệu thời khóa biểu độc lập theo từng tài khoản (userId).
 */
public class TimetableStore {
    private static final String PREF_NAME = "smart_timetable_prefs";
    private static final String KEY_PREFIX = "timetable_json_";

    private final SharedPreferences prefs;
    private final Gson gson;
    private final String currentUserId;

    public TimetableStore(Context context) {
        this(context, TokenStore.get(context).userId());
    }

    public TimetableStore(Context context, String userId) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.currentUserId = (userId != null && !userId.trim().isEmpty()) ? userId.trim() : "guest";
        // Tự động dọn dẹp key cũ từ phiên bản demo trước đó nếu còn sót lại trên bộ nhớ máy
        if (prefs.contains("timetable_json_data")) {
            prefs.edit().remove("timetable_json_data").apply();
        }
    }

    private String getKey() {
        return KEY_PREFIX + currentUserId;
    }

    /**
     * Dữ liệu mẫu trống - không chèn dữ liệu demo giả lập.
     */
    public static List<TimetableItem> getSampleTimetable() {
        return new ArrayList<>();
    }

    public static String getSampleJsonString() {
        return "[]";
    }

    public static String getBatchClassesSampleJsonString() {
        return "[\n" +
                "  {\n" +
                "    \"code\": \"INT101\",\n" +
                "    \"name\": \"Nhập môn công nghệ thông tin\",\n" +
                "    \"semester\": \"HK1 2026-2027\",\n" +
                "    \"dayOfWeek\": 2,\n" +
                "    \"shift\": \"Buổi sáng - 1,2,3\",\n" +
                "    \"room\": \"101 - A1\",\n" +
                "    \"studentIds\": []\n" +
                "  }\n" +
                "]";
    }

    /**
     * Cập nhật thời khóa biểu theo danh sách lớp học phần từ backend.
     * Luôn lưu lại: nếu danh sách rỗng (sinh viên chưa được thêm vào lớp nào) thì thời khóa biểu sẽ là rỗng [],
     * không bị dính lớp của tài khoản khác.
     */
    public void updateFromClasses(List<com.uni.attendance.net.Models.ClassView> classList, boolean isTeacher) {
        List<TimetableItem> items = new ArrayList<>();
        if (classList != null && !classList.isEmpty()) {
            for (com.uni.attendance.net.Models.ClassView c : classList) {
                int dow = c.dayOfWeek != null ? c.dayOfWeek : 2;
                String shift = c.shift != null && !c.shift.isEmpty() ? c.shift : "Buổi sáng - 4,5,6";
                String room = c.room != null && !c.room.isEmpty() ? c.room : "402 - A9";
                String lecturer = isTeacher ? "Giảng viên phụ trách" : (c.teacherName != null ? c.teacherName : "Giảng viên bộ môn");
                items.add(new TimetableItem(
                        dow,
                        "Lịch học",
                        shift,
                        c.name,
                        c.code,
                        lecturer,
                        room,
                        isTeacher ? ("Lớp " + c.code + " (" + c.studentCount + " SV)") : ("Tự động cập nhật theo lớp " + c.code)
                ));
            }
        }
        saveAll(items);
    }

    public List<TimetableItem> getAll() {
        String json = prefs.getString(getKey(), "[]");
        Type type = new TypeToken<List<TimetableItem>>() {}.getType();
        try {
            List<TimetableItem> list = gson.fromJson(json, type);
            return list != null ? list : new ArrayList<>();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public void saveAll(List<TimetableItem> items) {
        String json = gson.toJson(items != null ? items : new ArrayList<>());
        prefs.edit().putString(getKey(), json).apply();
    }

    public void clear() {
        prefs.edit().remove(getKey()).apply();
    }

    public boolean importFromJson(String json) {
        if (json == null || json.trim().isEmpty()) return false;
        try {
            Type type = new TypeToken<List<TimetableItem>>() {}.getType();
            List<TimetableItem> list = gson.fromJson(json, type);
            if (list != null) {
                saveAll(list);
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    public String exportToJson() {
        return gson.toJson(getAll());
    }

    /**
     * Lấy các buổi học của một thứ cụ thể (2=T2, ..., 7=T7, 8=CN)
     */
    public List<TimetableItem> getItemsForDay(int dayOfWeek) {
        List<TimetableItem> all = getAll();
        List<TimetableItem> result = new ArrayList<>();
        for (TimetableItem item : all) {
            if (item.dayOfWeek == dayOfWeek) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Đếm số môn học trong một thứ (để hiển thị chấm tròn và số lượng trên thanh tuần)
     */
    public int countForDay(int dayOfWeek) {
        int count = 0;
        for (TimetableItem item : getAll()) {
            if (item.dayOfWeek == dayOfWeek) count++;
        }
        return count;
    }

    /**
     * Lấy danh sách các môn học hôm nay theo ngày thực tế của thiết bị
     */
    public List<TimetableItem> getTodayItems() {
        Calendar cal = Calendar.getInstance();
        int day = cal.get(Calendar.DAY_OF_WEEK);
        // Calendar.SUNDAY = 1, MONDAY = 2, ..., SATURDAY = 7
        int dayOfWeek = (day == Calendar.SUNDAY) ? 8 : day;
        return getItemsForDay(dayOfWeek);
    }
}
