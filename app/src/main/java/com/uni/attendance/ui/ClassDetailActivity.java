package com.uni.attendance.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import okhttp3.ResponseBody;

/**
 * Quản lý một lớp học phần của giảng viên: danh sách sinh viên (thêm hàng loạt, nhấn giữ để xóa)
 * và báo cáo chuyên cần (xem trên màn hình hoặc xuất CSV cả lớp).
 */
public class ClassDetailActivity extends AppCompatActivity {
    public static final String EXTRA_CLASS_ID = "classId";
    private static final int MAX_BATCH = 500;

    private ApiService api;
    private long classId;
    private TextView titleView, infoView;
    private ListView listView;
    private Button reportButton;

    private Models.ClassDetail detail;
    private Models.ClassReport report;
    private boolean showingReport = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_class_detail);
        api = ApiClient.get(this);
        classId = getIntent().getLongExtra(EXTRA_CLASS_ID, -1);
        if (classId < 0) {
            finish();
            return;
        }
        View back = findViewById(R.id.btnBack);
        if (back != null) back.setOnClickListener(v -> finish());
        titleView = findViewById(R.id.listTitle);
        infoView = findViewById(R.id.infoView);
        listView = findViewById(R.id.listView);
        reportButton = findViewById(R.id.btnReport);
        listView.setEmptyView(findViewById(R.id.emptyView));

        findViewById(R.id.btnAddStudents).setOnClickListener(v -> showAddDialog());
        reportButton.setOnClickListener(v -> {
            showingReport = !showingReport;
            reload();
        });
        findViewById(R.id.btnExportClass).setOnClickListener(v -> chooseExportFormat());
        View deleteBtn = findViewById(R.id.btnDeleteClass);
        if (deleteBtn != null) {
            deleteBtn.setOnClickListener(v -> confirmDeleteClass());
        }
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (!showingReport && detail != null && position < detail.students.size()) {
                confirmRemove(detail.students.get(position));
            }
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        api.classDetail(classId).enqueue(new ApiCallback<Models.ClassDetail>(this) {
            @Override
            public void onSuccess(Models.ClassDetail d) {
                detail = d;
                if (showingReport) {
                    loadReport();
                } else {
                    render();
                }
            }

            @Override
            public void onError(String message, String code, int http) {
                toast(message);
            }
        });
    }

    private void loadReport() {
        api.classReport(classId).enqueue(new ApiCallback<Models.ClassReport>(this) {
            @Override
            public void onSuccess(Models.ClassReport r) {
                report = r;
                render();
            }

            @Override
            public void onError(String message, String code, int http) {
                showingReport = false;
                render();
                toast(message);
            }
        });
    }

    private void render() {
        if (detail == null) return;
        Models.ClassView info = detail.info;
        titleView.setText(info.code + " - " + info.name);
        List<String> lines = new ArrayList<>();

        if (showingReport && report != null) {
            reportButton.setText("Xem danh sách");
            infoView.setText(report.sessions.size() + " buổi điểm danh - " + report.rows.size() + " sinh viên");
            for (Models.ReportRow r : report.rows) {
                String detailLine = r.total == 0
                        ? "Chưa có buổi nào áp dụng"
                        : r.attended + "/" + r.total + " buổi - " + String.format("%.1f", r.ratePercent) + "%";
                lines.add(r.studentName + " (" + r.studentId + ")\n" + detailLine);
            }
        } else {
            showingReport = false;
            reportButton.setText("Báo cáo chuyên cần");
            infoView.setText((info.semester == null || info.semester.isEmpty() ? "" : info.semester + " - ")
                    + detail.students.size() + " sinh viên. Nhấn giữ một dòng để xóa khỏi lớp");
            for (Models.StudentView s : detail.students) {
                lines.add(s.name + " (" + s.studentId + ")");
            }
        }
        listView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, lines));
    }

    // ---------------- Thêm / xóa sinh viên ----------------
    private void showAddDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_students, null);
        EditText input = view.findViewById(R.id.studentsInput);
        new AlertDialog.Builder(this)
                .setTitle("Thêm sinh viên vào lớp")
                .setView(view)
                .setPositiveButton("Thêm", (d, w) -> {
                    // Tách theo dòng, dấu cách, dấu phẩy hoặc chấm phẩy (dán thẳng một cột từ Excel được)
                    Set<String> ids = new LinkedHashSet<>();
                    for (String token : input.getText().toString().split("[\\s,;]+")) {
                        if (!token.trim().isEmpty()) ids.add(token.trim());
                    }
                    if (ids.isEmpty()) {
                        toast("Chưa nhập mã sinh viên nào");
                    } else if (ids.size() > MAX_BATCH) {
                        toast("Mỗi lần thêm tối đa " + MAX_BATCH + " sinh viên");
                    } else {
                        addStudents(new ArrayList<>(ids));
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void addStudents(List<String> ids) {
        api.addStudents(classId, new Models.AddStudentsRequest(ids))
                .enqueue(new ApiCallback<Models.AddStudentsResult>(this) {
                    @Override
                    public void onSuccess(Models.AddStudentsResult r) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("Đã thêm: ").append(size(r.added));
                        sb.append("\nĐã có sẵn trong lớp: ").append(size(r.alreadyEnrolled));
                        if (size(r.notFound) > 0) {
                            sb.append("\nKhông thêm được: ").append(size(r.notFound)).append("\n")
                                    .append(join(r.notFound))
                                    .append("\n(chưa đăng ký tài khoản, sai mã hoặc không phải sinh viên)");
                        }
                        new AlertDialog.Builder(ClassDetailActivity.this)
                                .setTitle("Kết quả")
                                .setMessage(sb.toString())
                                .setPositiveButton("Đóng", null)
                                .show();
                        reload();
                    }

                    @Override
                    public void onError(String message, String code, int http) {
                        toast(message);
                    }
                });
    }

    private void confirmRemove(Models.StudentView student) {
        new AlertDialog.Builder(this)
                .setTitle("Xóa khỏi lớp")
                .setMessage("Xóa " + student.name + " (" + student.studentId + ") khỏi lớp? "
                        + "Các lần điểm danh cũ của sinh viên này vẫn được giữ.")
                .setPositiveButton("Xóa", (d, w) ->
                        api.removeStudent(classId, student.studentId).enqueue(new ApiCallback<Void>(this) {
                            @Override
                            public void onSuccess(Void body) {
                                reload();
                            }

                            @Override
                            public void onError(String message, String code, int http) {
                                toast(message);
                            }
                        }))
                .setNegativeButton("Hủy", null)
                .show();
    }

    // ---------------- Xuất file ----------------
    private void chooseExportFormat() {
        new AlertDialog.Builder(this)
                .setTitle("Xuất bảng điểm danh cả lớp")
                .setItems(new String[]{"Excel (.xlsx) - có định dạng", "CSV - dạng văn bản thuần"},
                        (d, which) -> export(which == 0))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void export(boolean xlsx) {
        retrofit2.Call<ResponseBody> call = xlsx ? api.exportClassXlsx(classId) : api.exportClass(classId);
        call.enqueue(new ApiCallback<ResponseBody>(this) {
            @Override
            public void onSuccess(ResponseBody body) {
                try {
                    CsvShare.share(ClassDetailActivity.this, body,
                            "class-" + classId + (xlsx ? ".xlsx" : ".csv"));
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

    private void confirmDeleteClass() {
        if (detail == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Xóa lớp học phần")
                .setMessage("Bạn có chắc chắn muốn xóa lớp " + detail.clazz.code + " (" + detail.clazz.name + ") không?\n\nLưu ý: Mọi buổi điểm danh và danh sách sinh viên thuộc lớp này sẽ bị xóa.")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    api.deleteClass(classId).enqueue(new ApiCallback<Void>(this) {
                        @Override
                        public void onSuccess(Void result) {
                            toast("Đã xóa lớp " + detail.clazz.code);
                            finish();
                        }

                        @Override
                        public void onError(String message, String code, int http) {
                            if (http == 405) {
                                api.deleteClassPost(classId).enqueue(new ApiCallback<Void>(ClassDetailActivity.this) {
                                    @Override
                                    public void onSuccess(Void result) {
                                        toast("Đã xóa lớp " + detail.clazz.code);
                                        finish();
                                    }

                                    @Override
                                    public void onError(String msg, String cd, int ht) {
                                        toast("Không thể xóa: " + msg);
                                    }
                                });
                            } else {
                                toast(message);
                            }
                        }
                    });
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // ---------------- Tiện ích ----------------
    private static int size(List<String> list) {
        return list == null ? 0 : list.size();
    }

    private static String join(List<String> list) {
        return list == null ? "" : String.join(", ", list);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
