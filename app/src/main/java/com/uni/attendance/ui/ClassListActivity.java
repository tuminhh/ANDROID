package com.uni.attendance.ui;

import android.content.Intent;
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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Danh sách lớp học phần.
 *  - Giảng viên: các lớp mình phụ trách, tạo lớp mới, chạm vào lớp để quản lý danh sách sinh viên
 *  - Sinh viên: các lớp mình có tên trong danh sách (chỉ xem)
 */
public class ClassListActivity extends AppCompatActivity {
    public static final String EXTRA_TEACHER = "teacher";
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{2,32}$");

    private ApiService api;
    private boolean teacher;
    private ListView listView;
    private final List<Models.ClassView> classes = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_class_list);
        api = ApiClient.get(this);
        teacher = getIntent().getBooleanExtra(EXTRA_TEACHER, false);

        View back = findViewById(R.id.btnBack);
        if (back != null) back.setOnClickListener(v -> finish());

        ((TextView) findViewById(R.id.listTitle))
                .setText(teacher ? "Lớp học phần của tôi" : "Lớp học phần của bạn");
        TextView empty = findViewById(R.id.emptyView);
        empty.setText(teacher
                ? "Chưa có lớp nào. Bấm \"Tạo lớp học phần\" để bắt đầu"
                : "Bạn chưa có tên trong lớp học phần nào. Hãy liên hệ giảng viên để được thêm vào danh sách lớp");
        listView = findViewById(R.id.listView);
        listView.setEmptyView(empty);

        View teacherLayout = findViewById(R.id.teacherActionLayout);
        if (teacherLayout != null) teacherLayout.setVisibility(teacher ? View.VISIBLE : View.GONE);

        View create = findViewById(R.id.btnCreateClass);
        if (create != null) create.setOnClickListener(v -> showCreateDialog());

        View importJson = findViewById(R.id.btnImportJson);
        if (importJson != null) importJson.setOnClickListener(v -> showBatchClassImportDialog());

        if (teacher) {
            listView.setOnItemClickListener((parent, view, position, id) -> openDetail(classes.get(position).id));
            listView.setOnItemLongClickListener((parent, view, position, id) -> {
                Models.ClassView c = classes.get(position);
                new AlertDialog.Builder(this)
                        .setTitle("Xóa lớp học phần")
                        .setMessage("Bạn có chắc chắn muốn xóa lớp " + c.code + " (" + c.name + ") không?\n\nLưu ý: Mọi buổi điểm danh và danh sách sinh viên thuộc lớp này sẽ bị xóa.")
                        .setPositiveButton("Xóa", (dialog, which) -> {
                            api.deleteClass(c.id).enqueue(new ApiCallback<Void>(this) {
                                @Override
                                public void onSuccess(Void result) {
                                    toast("Đã xóa lớp " + c.code);
                                    load();
                                }

                                @Override
                                public void onError(String message, String code, int http) {
                                    if (http == 405) {
                                        api.deleteClassPost(c.id).enqueue(new ApiCallback<Void>(ClassListActivity.this) {
                                            @Override
                                            public void onSuccess(Void result) {
                                                toast("Đã xóa lớp " + c.code);
                                                load();
                                            }

                                            @Override
                                            public void onError(String msg, String cd, int ht) {
                                                toast("Không thể xóa: " + msg + " (Hãy đảm bảo backend đã được rebuild)");
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
                return true;
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        retrofit2.Call<List<Models.ClassView>> call = teacher ? api.myClasses() : api.enrolledClasses();
        call.enqueue(new ApiCallback<List<Models.ClassView>>(this) {
            @Override
            public void onSuccess(List<Models.ClassView> list) {
                classes.clear();
                if (list != null) classes.addAll(list);
                List<String> lines = new ArrayList<>();
                for (Models.ClassView c : classes) {
                    StringBuilder sb = new StringBuilder(c.code).append(" - ").append(c.name).append('\n');
                    if (c.semester != null && !c.semester.isEmpty()) sb.append(c.semester).append(" - ");
                    if (!teacher) sb.append("GV ").append(c.teacherName).append(" - ");
                    sb.append(c.studentCount).append(" sinh viên");
                    lines.add(sb.toString());
                }
                listView.setAdapter(new ArrayAdapter<>(ClassListActivity.this,
                        android.R.layout.simple_list_item_1, lines));
            }

            @Override
            public void onError(String message, String code, int http) {
                toast(message);
            }
        });
    }

    private void showCreateDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_create_class, null);
        EditText codeInput = view.findViewById(R.id.codeInput);
        EditText nameInput = view.findViewById(R.id.nameInput);
        EditText semesterInput = view.findViewById(R.id.semesterInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Tạo lớp học phần")
                .setView(view)
                .setPositiveButton("Tạo", null)
                .setNegativeButton("Hủy", null)
                .create();
        // Ghi đè nút "Tạo" để dữ liệu sai thì hộp thoại không tự đóng
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            String code = codeInput.getText().toString().trim();
            String name = nameInput.getText().toString().trim();
            String semester = semesterInput.getText().toString().trim();
            if (!CODE_PATTERN.matcher(code).matches()) {
                toast("Mã lớp chỉ gồm chữ, số, dấu . _ - và dài 2-32 ký tự (không có dấu cách)");
                return;
            }
            if (name.isEmpty()) {
                toast("Nhập tên môn học");
                return;
            }
            dialog.dismiss();
            api.createClass(new Models.CreateClassRequest(code, name, semester.isEmpty() ? null : semester))
                    .enqueue(new ApiCallback<Models.ClassView>(this) {
                        @Override
                        public void onSuccess(Models.ClassView created) {
                            toast("Đã tạo lớp " + created.code + ". Hãy thêm danh sách sinh viên");
                            openDetail(created.id); // vào luôn để thêm sinh viên
                        }

                        @Override
                        public void onError(String message, String code, int http) {
                            toast(message);
                        }
                    });
        }));
        dialog.show();
    }

    private void showBatchClassImportDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_import_timetable_json, null);
        EditText jsonInput = view.findViewById(R.id.jsonInput);
        Button btnSample = view.findViewById(R.id.btnLoadSample);
        Button btnClear = view.findViewById(R.id.btnClear);

        jsonInput.setHint("[\n  {\n    \"code\": \"MÃ_LỚP\",\n    \"name\": \"Tên học phần\",\n    \"dayOfWeek\": 2,\n    \"shift\": \"Buổi sáng - 1,2,3\",\n    \"room\": \"Phòng học\",\n    \"studentIds\": [\"MÃ_SV\"]\n  }\n]");
        jsonInput.setText("");
        if (btnSample != null) {
            btnSample.setOnClickListener(v -> jsonInput.setText(com.uni.attendance.util.TimetableStore.getBatchClassesSampleJsonString()));
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
                                String msg = "Đã tạo mới " + res.createdCount + " lớp, cập nhật "
                                        + res.updatedCount + " lớp, ghi danh " + res.totalStudentsEnrolled + " sinh viên!";
                                new AlertDialog.Builder(ClassListActivity.this)
                                        .setTitle("Nhập lớp thành công")
                                        .setMessage(msg)
                                        .setPositiveButton("Đồng ý", (d, w) -> load())
                                        .show();
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
        List<com.uni.attendance.model.TimetableItem> tItems = new ArrayList<>();
        for (Models.BatchClassImportItem item : items) {
            int dow = item.dayOfWeek != null ? item.dayOfWeek : 2;
            String shift = item.shift != null && !item.shift.isEmpty() ? item.shift : "Buổi sáng - 4,5,6";
            String room = item.room != null && !item.room.isEmpty() ? item.room : "402 - A9";
            tItems.add(new com.uni.attendance.model.TimetableItem(
                    dow, "Lịch dạy", shift, item.name, item.code, "Giảng viên phụ trách", room, "Lớp " + item.code
            ));
        }
        new com.uni.attendance.util.TimetableStore(this).saveAll(tItems);

        for (Models.BatchClassImportItem item : items) {
            api.createClass(new Models.CreateClassRequest(item.code, item.name, item.semester))
                    .enqueue(new ApiCallback<Models.ClassView>(this) {
                        @Override
                        public void onSuccess(Models.ClassView created) {
                            if (item.studentIds != null && !item.studentIds.isEmpty()) {
                                api.addStudents(created.id, new Models.AddStudentsRequest(item.studentIds))
                                        .enqueue(new ApiCallback<Models.AddStudentsResult>(ClassListActivity.this) {
                                            @Override
                                            public void onSuccess(Models.AddStudentsResult res) {}
                                            @Override
                                            public void onError(String m, String c, int h) {}
                                        });
                            }
                            load();
                        }
                        @Override
                        public void onError(String m, String c, int h) {}
                    });
        }

        new AlertDialog.Builder(this)
                .setTitle("Đã nhập lớp học phần")
                .setMessage("Đã lưu các lớp học phần và cập nhật thời khóa biểu!")
                .setPositiveButton("Đồng ý", (d, w) -> load())
                .show();
    }

    private void openDetail(long classId) {
        Intent i = new Intent(this, ClassDetailActivity.class);
        i.putExtra(ClassDetailActivity.EXTRA_CLASS_ID, classId);
        startActivity(i);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
