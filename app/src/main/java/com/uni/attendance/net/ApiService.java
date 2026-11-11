package com.uni.attendance.net;

import com.uni.attendance.net.Models.AddStudentsRequest;
import com.uni.attendance.net.Models.AddStudentsResult;
import com.uni.attendance.net.Models.AttendanceRequest;
import com.uni.attendance.net.Models.AttendanceResult;
import com.uni.attendance.net.Models.AuthResponse;
import com.uni.attendance.net.Models.BatchClassImportItem;
import com.uni.attendance.net.Models.BatchClassImportResult;
import com.uni.attendance.net.Models.ClassDetail;
import com.uni.attendance.net.Models.ClassReport;
import com.uni.attendance.net.Models.ClassView;
import com.uni.attendance.net.Models.CreateClassRequest;
import com.uni.attendance.net.Models.FaceEnrollRequest;
import com.uni.attendance.net.Models.GeminiScanRequest;
import com.uni.attendance.net.Models.GeminiScanResponse;
import com.uni.attendance.net.Models.HistoryView;
import com.uni.attendance.net.Models.LoginRequest;
import com.uni.attendance.net.Models.ManualMarkRequest;
import com.uni.attendance.net.Models.OpenSessionRequest;
import com.uni.attendance.net.Models.RecordView;
import com.uni.attendance.net.Models.RegisterRequest;
import com.uni.attendance.net.Models.SessionRecords;
import com.uni.attendance.net.Models.SessionView;
import com.uni.attendance.net.Models.TeacherSessionView;
import com.uni.attendance.net.Models.TimetableItemDto;
import com.uni.attendance.net.Models.User;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

/** Khai báo các API của backend (xem README của backend). */
public interface ApiService {
    // ---------- Xác thực ----------
    @POST("api/auth/register")
    Call<AuthResponse> register(@Body RegisterRequest body);

    @POST("api/auth/login")
    Call<AuthResponse> login(@Body LoginRequest body);

    @GET("api/me")
    Call<User> me();

    @PUT("api/face")
    Call<Void> enrollFace(@Body FaceEnrollRequest body);

    // ---------- Lớp học phần ----------
    @POST("api/classes")
    Call<ClassView> createClass(@Body CreateClassRequest body);

    @POST("api/classes/import-json")
    Call<BatchClassImportResult> importClassesJson(@Body List<BatchClassImportItem> items);

    /** Các lớp do giảng viên phụ trách. */
    @GET("api/classes/mine")
    Call<List<ClassView>> myClasses();

    /** Các lớp mà sinh viên có tên trong danh sách. */
    @GET("api/classes/enrolled")
    Call<List<ClassView>> enrolledClasses();

    @GET("api/classes/{id}")
    Call<ClassDetail> classDetail(@Path("id") long id);

    @POST("api/classes/{id}/students")
    Call<AddStudentsResult> addStudents(@Path("id") long id, @Body AddStudentsRequest body);

    @DELETE("api/classes/{id}/students/{studentId}")
    Call<Void> removeStudent(@Path("id") long id, @Path("studentId") String studentId);

    @DELETE("api/classes/{id}")
    Call<Void> deleteClass(@Path("id") long id);

    @POST("api/classes/{id}/delete")
    Call<Void> deleteClassPost(@Path("id") long id);

    @GET("api/classes/{id}/report")
    Call<ClassReport> classReport(@Path("id") long id);

    /** CSV nhỏ nên để Retrofit đọc hết vào bộ nhớ (không dùng @Streaming) để xử lý được ở luồng chính. */
    @GET("api/classes/{id}/export")
    Call<ResponseBody> exportClass(@Path("id") long id);

    @GET("api/classes/{id}/export.xlsx")
    Call<ResponseBody> exportClassXlsx(@Path("id") long id);

    // ---------- Buổi điểm danh ----------
    @GET("api/sessions/active")
    Call<List<SessionView>> activeSessions();

    @GET("api/sessions/mine")
    Call<List<TeacherSessionView>> mySessions();

    @POST("api/sessions")
    Call<TeacherSessionView> openSession(@Body OpenSessionRequest body);

    @POST("api/sessions/{id}/close")
    Call<TeacherSessionView> closeSession(@Path("id") long id);

    @GET("api/sessions/{id}/records")
    Call<SessionRecords> records(@Path("id") long id);

    @GET("api/sessions/{id}/export")
    Call<ResponseBody> export(@Path("id") long id);

    @GET("api/sessions/{id}/export.xlsx")
    Call<ResponseBody> exportXlsx(@Path("id") long id);

    @POST("api/sessions/{id}/records")
    Call<RecordView> markManually(@Path("id") long id, @Body ManualMarkRequest body);

    @DELETE("api/sessions/{id}/records/{studentId}")
    Call<Void> deleteMark(@Path("id") long id, @Path("studentId") String studentId);

    // ---------- Điểm danh ----------
    @POST("api/attendance")
    Call<AttendanceResult> attend(@Body AttendanceRequest body);

    @GET("api/attendance/history")
    Call<List<HistoryView>> history();

    // ---------- Thời khóa biểu & Gemini AI ----------
    @GET("api/timetable/my")
    Call<List<TimetableItemDto>> getMyTimetable();

    @POST("api/timetable/import-json")
    Call<List<TimetableItemDto>> importTimetableJson(@Body List<TimetableItemDto> items);

    @POST("api/timetable/scan-gemini")
    Call<GeminiScanResponse> scanTimetableGemini(@Body GeminiScanRequest body);

    @DELETE("api/timetable/my")
    Call<Void> deleteMyTimetable();
}
