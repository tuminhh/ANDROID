package com.uni.attendance.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.uni.attendance.R;
import com.uni.attendance.net.ApiCallback;
import com.uni.attendance.net.ApiClient;
import com.uni.attendance.net.ApiService;
import com.uni.attendance.net.Models;
import com.uni.attendance.util.CsvShare;
import com.uni.attendance.util.TimeUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.ResponseBody;

/**
 * Hai chế độ:
 *  - MODE_HISTORY: sinh viên xem lịch sử điểm danh của mình
 *  - MODE_SESSION: giảng viên xem một buổi, gồm người có mặt và người vắng. Nhấn giữ dòng có mặt để xóa,
 *    nhấn giữ dòng vắng để đánh dấu có mặt. Có thêm thủ công theo mã và xuất CSV.
 */
public class RecordListActivity extends AppCompatActivity {
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_SESSION_ID = "sessionId";
    public static final int MODE_HISTORY = 0;
    public static final int MODE_SESSION = 1;

    private ApiService api;
    private TextView titleView;
    private ListView listView;
    private int mode;
    private long sessionId;
    private final List<Models.RecordView> present = new ArrayList<>();
    private final List<Models.StudentView> absent = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        api = ApiClient.get(this);
        View back = findViewById(R.id.btnBack);
        if (back != null) back.setOnClickListener(v -> finish());
        titleView = findViewById(R.id.listTitle);
        listView = findViewById(R.id.listView);
        listView.setEmptyView(findViewById(R.id.emptyView));

        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_HISTORY);
        sessionId = getIntent().getLongExtra(EXTRA_SESSION_ID, -1);

        if (mode == MODE_SESSION) {
            if (sessionId < 0) {
                finish();
                return;
            }
            findViewById(R.id.actionBar).setVisibility(View.VISIBLE);
            findViewById(R.id.btnMark).setOnClickListener(v -> askStudentToMark());
            findViewById(R.id.btnExport).setOnClickListener(v -> chooseExportFormat());
            listView.setOnItemLongClickListener((parent, view, position, id) -> {
                if (position < present.size()) {
                    confirmDelete(present.get(position));
                } else if (position - present.size() < absent.size()) {
                    confirmMarkPresent(absent.get(position - present.size()));
                }
                return true;
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mode == MODE_SESSION) {
            loadSession();
        } else {
            loadHistory();
        }
    }

    // ---------------- Sinh viên ----------------
    private void loadHistory() {
        titleView.setText("Lịch sử điểm danh của bạn");
        api.history().enqueue(new ApiCallback<List<Models.HistoryView>>(this) {
            @Override
            public void onSuccess(List<Models.HistoryView> list) {
                List<String> lines = new ArrayList<>();
                if (list != null) {
                    for (Models.HistoryView h : list) {
                        lines.add(h.sessionTitle + "\n" + TimeUtil.display(h.checkedAt) + " - "
                                + describe(h.method, h.similarity));
                    }
                }
                show(lines);
            }

            @Override
            public void onError(String message, String code, int http) {
                toast(message);
            }
        });
    }

    // ---------------- Giảng viên ----------------
    private void loadSession() {
        api.records(sessionId).enqueue(new ApiCallback<Models.SessionRecords>(this) {
            @Override
            public void onSuccess(Models.SessionRecords data) {
                present.clear();
                absent.clear();
                if (data.records != null) present.addAll(data.records);
                if (data.absent != null) absent.addAll(data.absent);

                Models.TeacherSessionView s = data.session;
                String classPart = s.classCode == null ? "" : s.classCode + " - ";
                titleView.setText(classPart + s.title + "\nCó mặt " + present.size() + "/" + data.rosterSize
                        + " - Vắng " + absent.size());

                List<String> lines = new ArrayList<>();
                for (Models.RecordView r : present) {
                    lines.add(r.studentName + " (" + r.studentId + ")\n" + TimeUtil.display(r.checkedAt)
                            + " - " + describe(r.method, r.similarity));
                }
                for (Models.StudentView a : absent) {
                    lines.add("VẮNG: " + a.name + " (" + a.studentId + ")\nNhấn giữ để đánh dấu có mặt");
                }
                show(lines);
            }

            @Override
            public void onError(String message, String code, int http) {
                toast(message);
            }
        });
    }

    private void askStudentToMark() {
        EditText input = new EditText(this);
        input.setHint("Mã sinh viên (phải thuộc lớp)");
        new AlertDialog.Builder(this)
                .setTitle("Điểm danh thủ công")
                .setView(input)
                .setPositiveButton("Thêm", (d, w) -> {
                    String id = input.getText().toString().trim();
                    if (id.isEmpty()) {
                        toast("Chưa nhập mã sinh viên");
                    } else {
                        markPresent(id);
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void confirmMarkPresent(Models.StudentView student) {
        new AlertDialog.Builder(this)
                .setTitle("Đánh dấu có mặt")
                .setMessage("Đánh dấu " + student.name + " (" + student.studentId + ") có mặt thủ công?")
                .setPositiveButton("Đánh dấu", (d, w) -> markPresent(student.studentId))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void markPresent(String studentId) {
        api.markManually(sessionId, new Models.ManualMarkRequest(studentId))
                .enqueue(new ApiCallback<Models.RecordView>(this) {
                    @Override
                    public void onSuccess(Models.RecordView r) {
                        toast("Đã đánh dấu " + r.studentName + " có mặt");
                        loadSession();
                    }

                    @Override
                    public void onError(String message, String code, int http) {
                        toast(message);
                    }
                });
    }

    private void confirmDelete(Models.RecordView record) {
        new AlertDialog.Builder(this)
                .setTitle("Xóa điểm danh")
                .setMessage("Xóa bản ghi của " + record.studentName + " (" + record.studentId + ")?")
                .setPositiveButton("Xóa", (d, w) ->
                        api.deleteMark(sessionId, record.studentId).enqueue(new ApiCallback<Void>(this) {
                            @Override
                            public void onSuccess(Void body) {
                                loadSession();
                            }

                            @Override
                            public void onError(String message, String code, int http) {
                                toast(message);
                            }
                        }))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void chooseExportFormat() {
        new AlertDialog.Builder(this)
                .setTitle("Xuất danh sách điểm danh")
                .setItems(new String[]{"Excel (.xlsx) - có định dạng", "CSV - dạng văn bản thuần"},
                        (d, which) -> export(which == 0))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void export(boolean xlsx) {
        retrofit2.Call<ResponseBody> call = xlsx ? api.exportXlsx(sessionId) : api.export(sessionId);
        call.enqueue(new ApiCallback<ResponseBody>(this) {
            @Override
            public void onSuccess(ResponseBody body) {
                try {
                    CsvShare.share(RecordListActivity.this, body,
                            "attendance-" + sessionId + (xlsx ? ".xlsx" : ".csv"));
                } catch (IOException e) {
                    toast("Không lưu được file: " + e.getMessage());
                }
            }

            @Override
            public void onError(String message, String code, int http) {
                toast(message);
            }
        });
    }

    // ---------------- Tiện ích ----------------
    private void show(List<String> lines) {
        listView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, lines));
    }

    private static String describe(String method, Double similarity) {
        if ("MANUAL".equals(method) || similarity == null) return "Thủ công";
        return String.format("Khuôn mặt, khớp %.0f%%", similarity * 100);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
