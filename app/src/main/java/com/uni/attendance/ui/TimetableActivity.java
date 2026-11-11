package com.uni.attendance.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
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
import com.uni.attendance.util.TimetableStore;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class TimetableActivity extends AppCompatActivity {
    private TimetableStore store;
    private TokenStore tokenStore;
    private ApiService api;

    private ListView scheduleListView;
    public static final String EXTRA_DAY_OF_WEEK = "extra_day_of_week";

    private View emptyScheduleView;
    private Calendar currentWeekMonday;
    private int selectedDayOfWeek = 2; // (2=T2 ... 7=T7, 8=CN)

    // Các thành phần UI của thanh 7 ngày trong tuần
    private final LinearLayout[] colDays = new LinearLayout[7];
    private final TextView[] lblDays = new TextView[7];
    private final LinearLayout[] pillDays = new LinearLayout[7];
    private final TextView[] dateDays = new TextView[7];
    private final View[] dotDays = new View[7];
    private final TextView[] cntDays = new TextView[7];

    private ScheduleAdapter adapter;
    private final List<TimetableItem> displayedItems = new ArrayList<>();

    public static int getTodayVnDayOfWeek() {
        Calendar cal = Calendar.getInstance();
        int day = cal.get(Calendar.DAY_OF_WEEK);
        // Calendar.SUNDAY = 1, MONDAY = 2, ..., SATURDAY = 7 -> Quy về chuẩn VN: 2=T2..7=T7, 8=CN
        return (day == Calendar.SUNDAY) ? 8 : day;
    }

    public static Calendar getMondayOfCurrentWeek() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        int day = cal.get(Calendar.DAY_OF_WEEK);
        int daysFromMonday = (day == Calendar.SUNDAY) ? 6 : (day - Calendar.MONDAY);
        cal.add(Calendar.DAY_OF_MONTH, -daysFromMonday);
        return cal;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_timetable);

        tokenStore = TokenStore.get(this);
        store = new TimetableStore(this, tokenStore.userId());
        api = ApiClient.get(this);

        scheduleListView = findViewById(R.id.scheduleListView);
        emptyScheduleView = findViewById(R.id.emptyScheduleView);
        adapter = new ScheduleAdapter();
        scheduleListView.setAdapter(adapter);

        // Khởi tạo tuần: Thứ Hai của tuần hiện tại (bảo đảm chuẩn xác độc lập mọi locale)
        currentWeekMonday = getMondayOfCurrentWeek();

        // Mặc định luôn trỏ đến đúng ngày hôm nay (hoặc thứ được truyền từ màn hình ngoài)
        int extraDay = getIntent().getIntExtra(EXTRA_DAY_OF_WEEK, -1);
        if (extraDay >= 2 && extraDay <= 8) {
            selectedDayOfWeek = extraDay;
        } else {
            selectedDayOfWeek = getTodayVnDayOfWeek();
        }

        bindWeekViews();
        setupEvents();
        updateWeekUi();
    }

    private void bindWeekViews() {
        int[] colIds = {R.id.colMon, R.id.colTue, R.id.colWed, R.id.colThu, R.id.colFri, R.id.colSat, R.id.colSun};
        int[] lblIds = {R.id.lblMon, R.id.lblTue, R.id.lblWed, R.id.lblThu, R.id.lblFri, R.id.lblSat, R.id.lblSun};
        int[] pillIds = {R.id.pillMon, R.id.pillTue, R.id.pillWed, R.id.pillThu, R.id.pillFri, R.id.pillSat, R.id.pillSun};
        int[] dateIds = {R.id.dateMon, R.id.dateTue, R.id.dateWed, R.id.dateThu, R.id.dateFri, R.id.dateSat, R.id.dateSun};
        int[] dotIds = {R.id.dotMon, R.id.dotTue, R.id.dotWed, R.id.dotThu, R.id.dotFri, R.id.dotSat, R.id.dotSun};
        int[] cntIds = {R.id.cntMon, R.id.cntTue, R.id.cntWed, R.id.cntThu, R.id.cntFri, R.id.cntSat, R.id.cntSun};

        for (int i = 0; i < 7; i++) {
            colDays[i] = findViewById(colIds[i]);
            lblDays[i] = findViewById(lblIds[i]);
            pillDays[i] = findViewById(pillIds[i]);
            dateDays[i] = findViewById(dateIds[i]);
            dotDays[i] = findViewById(dotIds[i]);
            cntDays[i] = findViewById(cntIds[i]);

            final int dayIndex = i + 2; // 2=T2 ... 8=CN
            colDays[i].setOnClickListener(v -> {
                selectedDayOfWeek = dayIndex;
                updateWeekUi();
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncWithBackend();
    }

    private void syncWithBackend() {
        boolean isTeacher = tokenStore.isTeacher();
        if (isTeacher) {
            api.myClasses().enqueue(new ApiCallback<List<Models.ClassView>>(this) {
                @Override
                public void onSuccess(List<Models.ClassView> list) {
                    if (list != null) {
                        store.updateFromClasses(list, true);
                        updateWeekUi();
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
                        store.updateFromClasses(list, false);
                        updateWeekUi();
                    }
                }
                @Override
                public void onError(String msg, String code, int http) {}
            });
        }
    }

    private void setupEvents() {
        boolean isTeacher = tokenStore.isTeacher();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnPrevWeek).setOnClickListener(v -> {
            currentWeekMonday.add(Calendar.DAY_OF_MONTH, -7);
            updateWeekUi();
        });
        findViewById(R.id.btnNextWeek).setOnClickListener(v -> {
            currentWeekMonday.add(Calendar.DAY_OF_MONTH, 7);
            updateWeekUi();
        });

        TextView titleView = findViewById(R.id.titleView);
        if (titleView != null) {
            titleView.setText(isTeacher ? "Lịch giảng dạy" : "Thời khóa biểu");
        }

        TextView extraLink = findViewById(R.id.btnExtraLink);
        if (extraLink != null) {
            extraLink.setText(isTeacher ? "Xem danh sách lớp của bạn" : "Tự động đồng bộ theo giảng viên");
            extraLink.setOnClickListener(v -> {
                if (isTeacher) {
                    Intent i = new Intent(this, ClassListActivity.class);
                    i.putExtra(ClassListActivity.EXTRA_TEACHER, true);
                    startActivity(i);
                } else {
                    syncWithBackend();
                    Toast.makeText(this, "Đang đồng bộ thời khóa biểu từ lớp học phần...", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Tóm tắt thời khóa biểu
        findViewById(R.id.btnSummary).setOnClickListener(v -> showTimetableSummaryDialog());

        // Nút nhập lớp học phần bằng JSON: CHỈ DÀNH CHO GIÁO VIÊN
        View btnJson = findViewById(R.id.btnJsonImport);
        View btnEmptyJson = findViewById(R.id.btnEmptyImportJson);

        if (isTeacher) {
            if (btnJson != null) {
                btnJson.setVisibility(View.VISIBLE);
                btnJson.setOnClickListener(v -> showBatchClassImportDialog());
            }
            if (btnEmptyJson != null) {
                btnEmptyJson.setVisibility(View.VISIBLE);
                btnEmptyJson.setOnClickListener(v -> showBatchClassImportDialog());
            }
        } else {
            // Học sinh không thể tự nhập thời khóa biểu hay nhập lớp JSON
            if (btnJson != null) btnJson.setVisibility(View.GONE);
            if (btnEmptyJson != null) btnEmptyJson.setVisibility(View.GONE);
        }
    }

    private void updateWeekUi() {
        Calendar cal = (Calendar) currentWeekMonday.clone();
        for (int i = 0; i < 7; i++) {
            int dayNumber = cal.get(Calendar.DAY_OF_MONTH);
            int currentDay = i + 2; // 2..8
            boolean isSelected = (currentDay == selectedDayOfWeek);

            dateDays[i].setText(String.valueOf(dayNumber));

            int classCount = store.countForDay(currentDay);

            if (isSelected) {
                pillDays[i].setBackgroundResource(R.drawable.bg_pill_selected);
                lblDays[i].setTextColor(getColor(R.color.brand_primary));
                lblDays[i].setTypeface(null, android.graphics.Typeface.BOLD);
                dateDays[i].setTextColor(0xFFFFFFFF);
                dateDays[i].setTypeface(null, android.graphics.Typeface.BOLD);

                if (classCount > 0) {
                    dotDays[i].setVisibility(View.VISIBLE);
                    dotDays[i].setBackgroundResource(R.drawable.shape_dot_white);
                    cntDays[i].setVisibility(View.VISIBLE);
                    cntDays[i].setText(String.valueOf(classCount));
                    cntDays[i].setTextColor(0xFFFFFFFF);
                } else {
                    dotDays[i].setVisibility(View.INVISIBLE);
                    cntDays[i].setVisibility(View.INVISIBLE);
                }
            } else {
                pillDays[i].setBackgroundResource(R.drawable.bg_pill_unselected);
                lblDays[i].setTextColor(getColor(R.color.text_secondary));
                lblDays[i].setTypeface(null, android.graphics.Typeface.NORMAL);
                dateDays[i].setTextColor(getColor(R.color.text_primary));
                dateDays[i].setTypeface(null, android.graphics.Typeface.NORMAL);

                if (classCount > 0) {
                    dotDays[i].setVisibility(View.VISIBLE);
                    dotDays[i].setBackgroundResource(R.drawable.shape_dot_orange);
                    cntDays[i].setVisibility(View.VISIBLE);
                    cntDays[i].setText(String.valueOf(classCount));
                    cntDays[i].setTextColor(getColor(R.color.brand_accent));
                } else {
                    dotDays[i].setVisibility(View.INVISIBLE);
                    cntDays[i].setVisibility(View.INVISIBLE);
                }
            }
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        // Tải danh sách môn học của ngày đã chọn
        displayedItems.clear();
        displayedItems.addAll(store.getItemsForDay(selectedDayOfWeek));
        adapter.notifyDataSetChanged();

        if (displayedItems.isEmpty()) {
            emptyScheduleView.setVisibility(View.VISIBLE);
            scheduleListView.setVisibility(View.GONE);
        } else {
            emptyScheduleView.setVisibility(View.GONE);
            scheduleListView.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Nhập lớp học phần bằng JSON (Dành riêng cho giáo viên).
     * Học sinh sẽ được tự động đồng bộ theo các lớp này.
     */
    private void showBatchClassImportDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_import_timetable_json, null);
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
                            Toast.makeText(this, "Dữ liệu JSON rỗng", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        api.importClassesJson(items).enqueue(new ApiCallback<Models.BatchClassImportResult>(this) {
                            @Override
                            public void onSuccess(Models.BatchClassImportResult res) {
                                String msg = "Đã tạo mới " + res.createdCount + " lớp, cập nhật "
                                        + res.updatedCount + " lớp, ghi danh " + res.totalStudentsEnrolled + " sinh viên!";
                                new AlertDialog.Builder(TimetableActivity.this)
                                        .setTitle("Nhập lớp học phần thành công")
                                        .setMessage(msg)
                                        .setPositiveButton("Đồng ý", null)
                                        .show();
                                syncWithBackend();
                            }

                            @Override
                            public void onError(String message, String code, int http) {
                                if (http == 405 || http == 404) {
                                    fallbackImportClasses(items);
                                } else {
                                    Toast.makeText(TimetableActivity.this, "Lỗi khi nhập lớp: " + message, Toast.LENGTH_LONG).show();
                                }
                            }
                        });
                    } catch (Exception e) {
                        Toast.makeText(this, "Định dạng JSON không hợp lệ: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
        store.saveAll(tItems);
        updateWeekUi();

        for (Models.BatchClassImportItem item : items) {
            api.createClass(new Models.CreateClassRequest(item.code, item.name, item.semester))
                    .enqueue(new ApiCallback<Models.ClassView>(this) {
                        @Override
                        public void onSuccess(Models.ClassView created) {
                            if (item.studentIds != null && !item.studentIds.isEmpty()) {
                                api.addStudents(created.id, new Models.AddStudentsRequest(item.studentIds))
                                        .enqueue(new ApiCallback<Models.AddStudentsResult>(TimetableActivity.this) {
                                            @Override
                                            public void onSuccess(Models.AddStudentsResult res) {}
                                            @Override
                                            public void onError(String m, String c, int h) {}
                                        });
                            }
                        }
                        @Override
                        public void onError(String m, String c, int h) {}
                    });
        }

        new AlertDialog.Builder(this)
                .setTitle("Đã nhập lớp học phần")
                .setMessage("Đã lưu lịch dạy vào Thời khóa biểu và đang đồng bộ " + items.size() + " lớp lên máy chủ!")
                .setPositiveButton("Đồng ý", (d, w) -> updateWeekUi())
                .show();
    }

    /**
     * Tóm tắt thời khóa biểu
     */
    private void showTimetableSummaryDialog() {
        boolean isTeacher = tokenStore.isTeacher();
        List<TimetableItem> all = store.getAll();
        StringBuilder sb = new StringBuilder();

        if (isTeacher) {
            sb.append("📋 TỔNG QUAN LỊCH GIẢNG DẠY:\n");
            sb.append("• Tổng số môn/lớp phụ trách: ").append(all.size()).append(" lớp\n\n");
            if (all.isEmpty()) {
                sb.append("Bạn chưa có lớp học phần nào. Bấm biểu tượng [ { } ] để nhập danh sách lớp bằng JSON.");
            } else {
                for (TimetableItem item : all) {
                    sb.append("• ").append(item.getDisplayDay()).append(": ")
                            .append(item.subjectName).append("\n  - Ca: ").append(item.shift)
                            .append("\n  - Phòng: ").append(item.room).append("\n\n");
                }
            }
        } else {
            sb.append("📚 TỔNG QUAN THỜI KHÓA BIỂU:\n");
            sb.append("• Tổng số môn học: ").append(all.size()).append(" môn\n");
            sb.append("• Tự động đồng bộ: Lịch học được cập nhật trực tiếp theo lớp do giảng viên phân công.\n\n");
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
                .setTitle(isTeacher ? "Tóm tắt lịch giảng dạy" : "Tóm tắt thời khóa biểu")
                .setMessage(sb.toString())
                .setPositiveButton("Đóng", null)
                .show();
    }

    /**
     * Adapter hiển thị danh sách thẻ môn học
     */
    private class ScheduleAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return displayedItems.size();
        }

        @Override
        public Object getItem(int position) {
            return displayedItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(TimetableActivity.this)
                        .inflate(R.layout.item_timetable_card, parent, false);
            }

            TimetableItem item = displayedItems.get(position);

            TextView shiftView = convertView.findViewById(R.id.shiftView);
            TextView subjectNameView = convertView.findViewById(R.id.subjectNameView);
            TextView lecturerView = convertView.findViewById(R.id.lecturerView);
            TextView roomView = convertView.findViewById(R.id.roomView);
            TextView noteView = convertView.findViewById(R.id.noteView);

            shiftView.setText(item.shift != null ? item.shift : "Buổi học");
            subjectNameView.setText(item.subjectName != null ? item.subjectName : "Môn học");
            lecturerView.setText(item.lecturer != null ? item.lecturer : "Chưa có giảng viên");
            roomView.setText(item.room != null ? item.room : "Chưa xếp phòng");

            if (item.note != null && !item.note.isEmpty()) {
                noteView.setVisibility(View.VISIBLE);
                noteView.setText(item.note);
            } else {
                noteView.setVisibility(View.GONE);
            }

            // Chạm vào thẻ môn học để mở thao tác
            convertView.setOnClickListener(v -> {
                boolean isTeacher = tokenStore.isTeacher();
                new AlertDialog.Builder(TimetableActivity.this)
                        .setTitle(item.subjectName)
                        .setMessage("Ca học: " + item.shift + "\nPhòng: " + item.room + "\nGiảng viên: " + item.lecturer)
                        .setPositiveButton(isTeacher ? "Mở điểm danh lớp này" : "Điểm danh môn này", (d, w) -> {
                            if (isTeacher) {
                                // Điều hướng tới màn hình danh sách lớp
                                Intent intent = new Intent(TimetableActivity.this, ClassListActivity.class);
                                intent.putExtra(ClassListActivity.EXTRA_TEACHER, true);
                                startActivity(intent);
                            } else {
                                // Điều hướng tới màn hình nhận diện khuôn mặt
                                Intent intent = new Intent(TimetableActivity.this, FaceActivity.class);
                                intent.putExtra(FaceActivity.EXTRA_MODE, FaceActivity.MODE_ATTEND);
                                startActivity(intent);
                            }
                        })
                        .setNegativeButton("Đóng", null)
                        .show();
            });

            return convertView;
        }
    }
}
