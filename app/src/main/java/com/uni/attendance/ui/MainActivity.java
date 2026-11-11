package com.uni.attendance.ui;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.uni.attendance.R;
import com.uni.attendance.model.TimetableItem;
import com.uni.attendance.net.ApiCallback;
import com.uni.attendance.net.ApiClient;
import com.uni.attendance.net.ApiService;
import com.uni.attendance.net.Models;
import com.uni.attendance.net.TokenStore;
import com.uni.attendance.util.LocationHelper;
import com.uni.attendance.util.TimeUtil;
import com.uni.attendance.util.TimetableStore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    /** Bán kính cho phép điểm danh quanh vị trí giảng viên khi bật giới hạn vị trí. */
    private static final int LOCATION_RADIUS_METERS = 100;

    private TokenStore store;
    private TimetableStore timetableStore;
    private ApiService api;
    private TextView statusView;
    private TextView timetableDateView, timetableSummaryView;
    private boolean teacher;

    // Dữ liệu mới nhất lấy từ máy chủ
    private Models.User me;
    private List<Models.SessionView> activeSessions = new ArrayList<>();
    private List<Models.TeacherSessionView> mySessions = new ArrayList<>();

    // Buổi đang chờ cấp quyền vị trí để mở
    private long pendingClassId;
    private String pendingTitle;
    private int pendingMinutes;

    private final ActivityResultLauncher<String[]> locationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))
                        && pendingTitle != null) {
                    openWithLocation(pendingClassId, pendingTitle, pendingMinutes);
                } else {
                    toast("Cần quyền vị trí chính xác để giới hạn phạm vi điểm danh");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        store = TokenStore.get(this);
        teacher = store.isTeacher();
        timetableStore = new TimetableStore(this, store.userId());
        api = ApiClient.get(this);
        statusView = findViewById(R.id.statusView);
        timetableDateView = findViewById(R.id.timetableDateView);
        timetableSummaryView = findViewById(R.id.timetableSummaryView);
        updateTimetableCard();

        ((TextView) findViewById(R.id.welcomeView)).setText("Hi, " + store.name());
        TextView badge = findViewById(R.id.userRoleBadge);
        if (badge != null) {
            badge.setText((teacher ? "Giảng viên" : "Sinh viên") + " • " + store.userId());
        }

        TextView headerLabel = findViewById(R.id.timetableHeaderLabel);
        if (headerLabel != null) {
            headerLabel.setText(teacher ? "Lịch giảng dạy hôm nay" : "Thời khóa biểu hôm nay");
        }

        // Tùy biến giao diện riêng biệt cho Giảng viên và Sinh viên
        View teacherGrid = findViewById(R.id.teacherGrid);
        View studentGrid = findViewById(R.id.studentGrid);
        if (teacherGrid != null) teacherGrid.setVisibility(teacher ? View.VISIBLE : View.GONE);
        if (studentGrid != null) studentGrid.setVisibility(teacher ? View.GONE : View.VISIBLE);

        // Mở Thời khóa biểu (luôn truyền thứ hôm nay sang)
        View.OnClickListener openTimetable = v -> {
            Intent intent = new Intent(this, TimetableActivity.class);
            Calendar now = Calendar.getInstance();
            int day = now.get(Calendar.DAY_OF_WEEK);
            int vnDow = (day == Calendar.SUNDAY) ? 8 : day;
            intent.putExtra(TimetableActivity.EXTRA_DAY_OF_WEEK, vnDow);
            startActivity(intent);
        };
        findViewById(R.id.cardTimetable).setOnClickListener(openTimetable);
        findViewById(R.id.btnViewTimetable).setOnClickListener(openTimetable);

        if (teacher) {
            // Các nút dành riêng cho Giảng viên
            findViewById(R.id.btnClasses).setOnClickListener(v -> {
                Intent i = new Intent(this, ClassListActivity.class);
                i.putExtra(ClassListActivity.EXTRA_TEACHER, true);
                startActivity(i);
            });
            findViewById(R.id.btnTeacherImportJson).setOnClickListener(v -> showBatchClassImportDialog());
            findViewById(R.id.btnOpenSession).setOnClickListener(v -> chooseClassToOpen());
            findViewById(R.id.btnViewRecords).setOnClickListener(v -> chooseSessionToView());
            findViewById(R.id.btnTeacherTimetable).setOnClickListener(openTimetable);
            findViewById(R.id.btnTeacherSummary).setOnClickListener(v -> showTimetableSummaryDialog());
            View btnClose = findViewById(R.id.btnCloseSession);
            if (btnClose != null) btnClose.setOnClickListener(v -> closeActiveSession());
        } else {
            // Các nút dành riêng cho Sinh viên
            findViewById(R.id.btnStudentTimetable).setOnClickListener(openTimetable);
            findViewById(R.id.btnAttend).setOnClickListener(v -> onAttendClicked());
            findViewById(R.id.btnEnroll).setOnClickListener(v -> onEnrollClicked());
            findViewById(R.id.btnStudentClasses).setOnClickListener(v -> {
                Intent i = new Intent(this, ClassListActivity.class);
                i.putExtra(ClassListActivity.EXTRA_TEACHER, false);
                startActivity(i);
            });
            findViewById(R.id.btnHistory).setOnClickListener(v -> {
                Intent i = new Intent(this, RecordListActivity.class);
                i.putExtra(RecordListActivity.EXTRA_MODE, RecordListActivity.MODE_HISTORY);
                startActivity(i);
            });
            findViewById(R.id.btnStudentSummary).setOnClickListener(v -> showTimetableSummaryDialog());
        }

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            store.clear();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateTimetableCard();
        refresh();
        syncClassesToTimetable();
    }

    /**
     * Tự động đồng bộ thời khóa biểu của sinh viên/giảng viên theo các lớp học phần từ backend
     */
    private void syncClassesToTimetable() {
        if (teacher) {
            api.myClasses().enqueue(new ApiCallback<List<Models.ClassView>>(this) {
                @Override
                public void onSuccess(List<Models.ClassView> list) {
                    if (list != null) {
                        timetableStore.updateFromClasses(list, true);
                        updateTimetableCard();
                    }
                }
                @Override
                public void onError(String msg, String code, int http) {}
            });
        } else {
            api.enrolledClasses().enqueue(new ApiCallback<List<Models.ClassView>>(this) {
                @Override
                public void onSuccess(List<Models.ClassView> list) {
                    if (list != null) {
                        timetableStore.updateFromClasses(list, false);
                        updateTimetableCard();
                    }
                }
                @Override
                public void onError(String msg, String code, int http) {}
            });
        }
    }

    private void updateTimetableCard() {
        if (timetableDateView == null || timetableSummaryView == null) return;
        Calendar now = Calendar.getInstance();

        int dayOfWeek = now.get(Calendar.DAY_OF_WEEK);
        int vnDayOfWeek = (dayOfWeek == Calendar.SUNDAY) ? 8 : dayOfWeek;
        String dayName = getVnDayName(vnDayOfWeek);

        SimpleDateFormat sdf = new SimpleDateFormat("d 'tháng' M", new Locale("vi", "VN"));
        String dateFormatted = sdf.format(now.getTime());

        // Hiển thị đầy đủ cả Thứ và Ngày tháng hiện tại (ví dụ: "Thứ 4, 11 tháng 11")
        timetableDateView.setText(dayName + ", " + dateFormatted);

        List<TimetableItem> todayClasses = timetableStore.getTodayItems();
        if (todayClasses.isEmpty()) {
            timetableSummaryView.setText(teacher
                    ? "Hôm nay (" + dayName + ") không có lịch dạy! Bấm để xem TKB"
                    : "Hôm nay (" + dayName + ") không có lịch học! Xem thêm TKB");
        } else {
            TimetableItem first = todayClasses.get(0);
            StringBuilder sb = new StringBuilder(teacher ? "Hôm nay dạy: " : "Hôm nay học: ")
                    .append(first.subjectName);
            if (first.room != null && !first.room.isEmpty()) {
                sb.append(" (").append(first.room).append(")");
            }
            if (todayClasses.size() > 1) {
                sb.append(" (+").append(todayClasses.size() - 1).append(" môn nữa)");
            }
            timetableSummaryView.setText(sb.toString());
        }
    }

    private String getVnDayName(int vnDayOfWeek) {
        switch (vnDayOfWeek) {
            case 2: return "Thứ 2";
            case 3: return "Thứ 3";
            case 4: return "Thứ 4";
            case 5: return "Thứ 5";
            case 6: return "Thứ 6";
            case 7: return "Thứ 7";
            case 8: return "Chủ nhật";
            default: return "Thứ " + vnDayOfWeek;
        }
    }

    /**
     * Nhập hàng loạt lớp học phần (kèm lịch TKB và danh sách sinh viên) bằng JSON - Dành cho giáo viên
     */
    private void showBatchClassImportDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_import_timetable_json, null);
        TextView title = view.findViewById(android.R.id.title);
        EditText jsonInput = view.findViewById(R.id.jsonInput);
        Button btnSample = view.findViewById(R.id.btnLoadSample);
        Button btnClear = view.findViewById(R.id.btnClear);

        jsonInput.setHint("[\n  {\n    \"code\": \"MÃ_LỚP\",\n    \"name\": \"Tên học phần\",\n    \"dayOfWeek\": 2,\n    \"shift\": \"Buổi sáng - 1,2,3\",\n    \"room\": \"Phòng học\",\n    \"studentIds\": [\"MÃ_SV\"]\n  }\n]");
        jsonInput.setText("");

        if (btnSample != null) {
            btnSample.setOnClickListener(v -> jsonInput.setText(TimetableStore.getBatchClassesSampleJsonString()));
        }
        if (btnClear != null) {
            btnClear.setOnClickListener(v -> jsonInput.setText(""));
        }

        new AlertDialog.Builder(this)
                .setTitle("Nhập lớp học phần bằng JSON (Giảng viên)")
                .setView(view)
                .setPositiveButton("Lưu & Tạo lớp", (dialog, which) -> {
                    String json = jsonInput.getText().toString().trim();
                    try {
                        java.lang.reflect.Type type = new com.google.gson.reflect.TypeToken<List<Models.BatchClassImportItem>>() {}.getType();
                        List<Models.BatchClassImportItem> items = new com.google.gson.Gson().fromJson(json, type);
                        if (items == null || items.isEmpty()) {
                            toast("Dữ liệu JSON rỗng");
                            return;
                        }

                        api.importClassesJson(items).enqueue(new ApiCallback<Models.BatchClassImportResult>(this) {
                            @Override
                            public void onSuccess(Models.BatchClassImportResult res) {
                                String msg = "Đã xử lý: Tạo mới " + res.createdCount + " lớp, cập nhật "
                                        + res.updatedCount + " lớp, ghi danh " + res.totalStudentsEnrolled + " sinh viên!";
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle("Nhập lớp học phần thành công")
                                        .setMessage(msg)
                                        .setPositiveButton("Đồng ý", null)
                                        .show();
                                syncClassesToTimetable();
                            }

                            @Override
                            public void onError(String message, String code, int http) {
                                if (http == 405 || http == 404) {
                                    fallbackImportClasses(items);
                                } else {
                                    toast("Lỗi khi nhập lớp: " + message);
                                }
                            }
                        });
                    } catch (Exception e) {
                        toast("Định dạng JSON không hợp lệ: " + e.getMessage());
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void fallbackImportClasses(List<Models.BatchClassImportItem> items) {
        List<TimetableItem> tItems = new ArrayList<>();
        for (Models.BatchClassImportItem item : items) {
            int dow = item.dayOfWeek != null ? item.dayOfWeek : 2;
            String shift = item.shift != null && !item.shift.isEmpty() ? item.shift : "Buổi sáng - 4,5,6";
            String room = item.room != null && !item.room.isEmpty() ? item.room : "402 - A9";
            tItems.add(new TimetableItem(
                    dow, "Lịch dạy", shift, item.name, item.code, "Giảng viên phụ trách", room, "Lớp " + item.code
            ));
        }
        timetableStore.saveAll(tItems);
        updateTimetableCard();

        for (Models.BatchClassImportItem item : items) {
            api.createClass(new Models.CreateClassRequest(item.code, item.name, item.semester))
                    .enqueue(new ApiCallback<Models.ClassView>(this) {
                        @Override
                        public void onSuccess(Models.ClassView created) {
                            if (item.studentIds != null && !item.studentIds.isEmpty()) {
                                api.addStudents(created.id, new Models.AddStudentsRequest(item.studentIds))
                                        .enqueue(new ApiCallback<Models.AddStudentsResult>(MainActivity.this) {
                                            @Override
                                            public void onSuccess(Models.AddStudentsResult res) {}
                                            @Override
                                            public void onError(String m, String c, int h) {}
                                        });
                            }
                            syncClassesToTimetable();
                        }
                        @Override
                        public void onError(String m, String c, int h) {}
                    });
        }

        new AlertDialog.Builder(this)
                .setTitle("Đã nhập lớp học phần")
                .setMessage("Đã lưu lịch dạy vào Thời khóa biểu và đang đồng bộ " + items.size() + " lớp lên máy chủ!")
                .setPositiveButton("Đồng ý", (d, w) -> updateTimetableCard())
                .show();
    }

    /**
     * Tóm tắt thời khóa biểu
     */
    private void showTimetableSummaryDialog() {
        List<TimetableItem> all = timetableStore.getAll();
        StringBuilder sb = new StringBuilder();
        if (teacher) {
            sb.append("📋 TỔNG QUAN LỊCH GIẢNG DẠY:\n");
            sb.append("• Tổng số môn/lớp phụ trách: ").append(all.size()).append(" lớp\n\n");
            if (all.isEmpty()) {
                sb.append("Bạn chưa có lớp học phần nào. Bấm 'Nhập lớp JSON' để thêm nhanh.");
            } else {
                for (TimetableItem item : all) {
                    sb.append("• ").append(item.getDisplayDay()).append(": ")
                            .append(item.subjectName).append("\n  - Ca: ").append(item.shift)
                            .append("\n  - Phòng: ").append(item.room).append("\n\n");
                }
            }
        } else {
            sb.append("📚 TỔNG QUAN THỜI KHÓA BIỂU CỦA BẠN:\n");
            sb.append("• Tổng số môn học: ").append(all.size()).append(" môn\n");
            sb.append("• Tình trạng: Lịch học được tự động đồng bộ theo các lớp học phần từ giảng viên.\n\n");
            if (all.isEmpty()) {
                sb.append("Hiện chưa có môn học nào. Hãy liên hệ giảng viên để được ghi danh vào lớp.");
            } else {
                for (TimetableItem item : all) {
                    sb.append("• ").append(item.getDisplayDay()).append(": ")
                            .append(item.subjectName).append("\n  - Ca: ").append(item.shift)
                            .append("\n  - Phòng: ").append(item.room)
                            .append("\n  - Giảng viên: ").append(item.lecturer).append("\n\n");
                }
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(teacher ? "Tóm tắt lịch giảng dạy" : "Tóm tắt thời khóa biểu")
                .setMessage(sb.toString())
                .setPositiveButton("Đóng", null)
                .show();
    }

    // ---------------- Tải dữ liệu ----------------
    private void refresh() {
        if (teacher) {
            api.mySessions().enqueue(new ApiCallback<List<Models.TeacherSessionView>>(this) {
                @Override
                public void onSuccess(List<Models.TeacherSessionView> list) {
                    mySessions = list == null ? new ArrayList<>() : list;
                    Models.TeacherSessionView open = findActive();
                    View card = findViewById(R.id.activeSessionCard);
                    TextView title = findViewById(R.id.activeSessionTitle);
                    TextView info = findViewById(R.id.activeSessionInfo);

                    if (open != null) {
                        if (card != null) card.setVisibility(View.VISIBLE);
                        if (title != null) title.setText(sessionLabel(open.classCode, open.title));
                        if (info != null) {
                            info.setText("Còn " + TimeUtil.minutesUntil(open.expiresAt)
                                    + " phút • " + open.presentCount + " sinh viên đã có mặt");
                        }
                        statusView.setText("");
                    } else {
                        if (card != null) card.setVisibility(View.GONE);
                        statusView.setText("Hiện không có buổi điểm danh nào đang mở");
                    }
                }

                @Override
                public void onError(String message, String code, int http) {
                    statusView.setText(message);
                }
            });
        } else {
            api.me().enqueue(new ApiCallback<Models.User>(this) {
                @Override
                public void onSuccess(Models.User user) {
                    me = user;
                }

                @Override
                public void onError(String message, String code, int http) {
                    statusView.setText(message);
                }
            });
            api.activeSessions().enqueue(new ApiCallback<List<Models.SessionView>>(this) {
                @Override
                public void onSuccess(List<Models.SessionView> list) {
                    activeSessions = list == null ? new ArrayList<>() : list;
                    if (activeSessions.isEmpty()) {
                        statusView.setText("Hiện không có buổi điểm danh nào đang mở cho các lớp của bạn");
                    } else {
                        StringBuilder sb = new StringBuilder("Đang mở:");
                        for (Models.SessionView s : activeSessions) {
                            sb.append("\n• ").append(sessionLabel(s.classCode, s.title))
                                    .append(" (").append(s.teacherName).append(")");
                        }
                        statusView.setText(sb.toString());
                    }
                }

                @Override
                public void onError(String message, String code, int http) {
                    statusView.setText(message);
                }
            });
        }
    }

    private Models.TeacherSessionView findActive() {
        for (Models.TeacherSessionView s : mySessions) {
            if (s.active) return s;
        }
        return null;
    }

    private static String sessionLabel(String classCode, String title) {
        return classCode == null || classCode.isEmpty() ? title : classCode + " - " + title;
    }

    // ---------------- Sinh viên ----------------
    private void onEnrollClicked() {
        if (me == null) {
            toast("Đang tải thông tin, hãy thử lại sau giây lát");
            refresh();
            return;
        }
        if (me.faceEnrolled) {
            // Đăng ký lại phải xác nhận mật khẩu
            EditText pass = new EditText(this);
            pass.setHint("Mật khẩu");
            pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            new AlertDialog.Builder(this)
                    .setTitle("Đăng ký lại khuôn mặt")
                    .setMessage("Nhập mật khẩu để xác nhận")
                    .setView(pass)
                    .setPositiveButton("Tiếp tục", (d, w) -> {
                        String text = pass.getText().toString();
                        if (text.isEmpty()) {
                            toast("Chưa nhập mật khẩu");
                        } else {
                            startEnroll(text);
                        }
                    })
                    .setNegativeButton("Hủy", null)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Đồng ý sử dụng dữ liệu khuôn mặt")
                    .setMessage("Ứng dụng sẽ chuyển khuôn mặt của bạn thành một vector số và gửi lên máy chủ "
                            + "của trường để điểm danh. Ảnh khuôn mặt không được lưu. "
                            + "Bạn có đồng ý không?")
                    .setPositiveButton("Đồng ý", (d, w) -> startEnroll(null))
                    .setNegativeButton("Không", null)
                    .show();
        }
    }

    private void startEnroll(String password) {
        Intent i = new Intent(this, FaceActivity.class);
        i.putExtra(FaceActivity.EXTRA_MODE, FaceActivity.MODE_ENROLL);
        i.putExtra(FaceActivity.EXTRA_PASSWORD, password);
        startActivity(i);
    }

    private void onAttendClicked() {
        if (me == null) {
            toast("Đang tải thông tin, hãy thử lại sau giây lát");
            refresh();
        } else if (!me.faceEnrolled) {
            toast("Bạn chưa đăng ký khuôn mặt");
        } else if (activeSessions.isEmpty()) {
            toast("Hiện không có buổi điểm danh nào đang mở cho các lớp của bạn");
        } else if (activeSessions.size() == 1) {
            startAttend(activeSessions.get(0));
        } else {
            String[] labels = new String[activeSessions.size()];
            for (int i = 0; i < labels.length; i++) {
                Models.SessionView s = activeSessions.get(i);
                labels[i] = sessionLabel(s.classCode, s.title) + " (" + s.teacherName + ")";
            }
            new AlertDialog.Builder(this)
                    .setTitle("Chọn buổi điểm danh")
                    .setItems(labels, (d, which) -> startAttend(activeSessions.get(which)))
                    .setNegativeButton("Hủy", null)
                    .show();
        }
    }

    private void startAttend(Models.SessionView session) {
        Intent i = new Intent(this, FaceActivity.class);
        i.putExtra(FaceActivity.EXTRA_MODE, FaceActivity.MODE_ATTEND);
        i.putExtra(FaceActivity.EXTRA_SESSION_ID, session.id);
        i.putExtra(FaceActivity.EXTRA_LOCATION_REQUIRED, session.locationRequired);
        startActivity(i);
    }

    // ---------------- Giảng viên ----------------
    /** Buổi điểm danh luôn thuộc một lớp học phần: chọn lớp trước rồi mới nhập thông tin buổi. */
    private void chooseClassToOpen() {
        api.myClasses().enqueue(new ApiCallback<List<Models.ClassView>>(this) {
            @Override
            public void onSuccess(List<Models.ClassView> list) {
                if (list == null || list.isEmpty()) {
                    toast("Hãy tạo lớp học phần trước (mục Lớp học phần)");
                } else if (list.size() == 1) {
                    showOpenSessionDialog(list.get(0));
                } else {
                    String[] labels = new String[list.size()];
                    for (int i = 0; i < labels.length; i++) {
                        Models.ClassView c = list.get(i);
                        labels[i] = c.code + " - " + c.name + " (" + c.studentCount + " SV)";
                    }
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Chọn lớp học phần")
                            .setItems(labels, (d, which) -> showOpenSessionDialog(list.get(which)))
                            .setNegativeButton("Hủy", null)
                            .show();
                }
            }

            @Override
            public void onError(String message, String code, int http) {
                toast(message);
            }
        });
    }

    private void showOpenSessionDialog(Models.ClassView clazz) {
        if (clazz.studentCount == 0) {
            toast("Lớp " + clazz.code + " chưa có sinh viên. Hãy thêm danh sách lớp trước");
            return;
        }
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_open_session, null);
        EditText titleInput = view.findViewById(R.id.titleInput);
        EditText minutesInput = view.findViewById(R.id.minutesInput);
        CheckBox locationCheck = view.findViewById(R.id.locationCheck);
        locationCheck.setText("Chỉ cho điểm danh trong bán kính " + LOCATION_RADIUS_METERS
                + " m quanh vị trí hiện tại của tôi");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Mở buổi điểm danh - " + clazz.code)
                .setView(view)
                .setPositiveButton("Mở", null)
                .setNegativeButton("Hủy", null)
                .create();
        // Ghi đè nút "Mở" để dữ liệu sai thì hộp thoại không tự đóng
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String title = titleInput.getText().toString().trim();
            int minutes;
            try {
                minutes = Integer.parseInt(minutesInput.getText().toString().trim());
            } catch (NumberFormatException e) {
                minutes = -1;
            }
            if (title.isEmpty()) {
                toast("Hãy nhập tên buổi học");
                return;
            }
            if (minutes < 1 || minutes > 240) {
                toast("Thời lượng phải từ 1 đến 240 phút");
                return;
            }
            dialog.dismiss();
            if (locationCheck.isChecked()) {
                pendingClassId = clazz.id;
                pendingTitle = title;
                pendingMinutes = minutes;
                locationPermission.launch(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION});
            } else {
                openSession(new Models.OpenSessionRequest(clazz.id, title, minutes, null, null, null));
            }
        }));
        dialog.show();
    }

    private void openWithLocation(long classId, String title, int minutes) {
        LocationHelper.current(this, new LocationHelper.Listener() {
            @Override
            public void onLocation(double latitude, double longitude) {
                openSession(new Models.OpenSessionRequest(classId, title, minutes, latitude, longitude,
                        LOCATION_RADIUS_METERS));
            }

            @Override
            public void onError(String message) {
                toast(message);
            }
        });
    }

    private void openSession(Models.OpenSessionRequest request) {
        api.openSession(request).enqueue(new ApiCallback<Models.TeacherSessionView>(this) {
            @Override
            public void onSuccess(Models.TeacherSessionView s) {
                toast("Đã mở buổi điểm danh");
                refresh();
            }

            @Override
            public void onError(String message, String code, int http) {
                toast(message);
            }
        });
    }

    private void closeActiveSession() {
        Models.TeacherSessionView open = findActive();
        if (open == null) {
            toast("Hiện không có buổi nào đang mở");
            return;
        }
        api.closeSession(open.id).enqueue(new ApiCallback<Models.TeacherSessionView>(this) {
            @Override
            public void onSuccess(Models.TeacherSessionView s) {
                toast("Đã đóng buổi điểm danh");
                refresh();
            }

            @Override
            public void onError(String message, String code, int http) {
                toast(message);
            }
        });
    }

    private void chooseSessionToView() {
        if (mySessions.isEmpty()) {
            toast("Bạn chưa mở buổi điểm danh nào");
            return;
        }
        String[] labels = new String[mySessions.size()];
        for (int i = 0; i < labels.length; i++) {
            Models.TeacherSessionView s = mySessions.get(i);
            labels[i] = sessionLabel(s.classCode, s.title) + "\n" + TimeUtil.display(s.openedAt)
                    + " - " + s.presentCount + " có mặt" + (s.active ? " (đang mở)" : "");
        }
        new AlertDialog.Builder(this)
                .setTitle("Chọn buổi để xem")
                .setItems(labels, (d, which) -> {
                    Intent i = new Intent(this, RecordListActivity.class);
                    i.putExtra(RecordListActivity.EXTRA_MODE, RecordListActivity.MODE_SESSION);
                    i.putExtra(RecordListActivity.EXTRA_SESSION_ID, mySessions.get(which).id);
                    startActivity(i);
                })
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
