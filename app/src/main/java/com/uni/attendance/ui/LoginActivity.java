package com.uni.attendance.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.uni.attendance.R;
import com.uni.attendance.net.ApiCallback;
import com.uni.attendance.net.ApiClient;
import com.uni.attendance.net.ApiService;
import com.uni.attendance.net.Models;
import com.uni.attendance.net.TokenStore;

import java.util.regex.Pattern;

public class LoginActivity extends AppCompatActivity {
    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{3,32}$");

    private TextView tabLogin, tabRegister;
    private View layoutRoleSection, layoutNameSection, layoutInviteSection;
    private RadioGroup radioGroupRole;
    private RadioButton radioStudent, radioTeacher;
    private TextView lblId, lblPass, txtSwitchMode;
    private EditText idInput, nameInput, passInput, inviteInput;
    private Button btnSubmit;

    private boolean isRegisterMode = false;
    private TokenStore store;
    private ApiService api;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = TokenStore.get(this);
        if (store.isLoggedIn()) {
            openMain();
            return;
        }
        api = ApiClient.get(this);
        setContentView(R.layout.activity_login);

        tabLogin = findViewById(R.id.tabLogin);
        tabRegister = findViewById(R.id.tabRegister);
        layoutRoleSection = findViewById(R.id.layoutRoleSection);
        layoutNameSection = findViewById(R.id.layoutNameSection);
        layoutInviteSection = findViewById(R.id.layoutInviteSection);
        radioGroupRole = findViewById(R.id.radioGroupRole);
        radioStudent = findViewById(R.id.radioStudent);
        radioTeacher = findViewById(R.id.radioTeacher);
        lblId = findViewById(R.id.lblId);
        lblPass = findViewById(R.id.lblPass);
        idInput = findViewById(R.id.idInput);
        nameInput = findViewById(R.id.nameInput);
        passInput = findViewById(R.id.passInput);
        inviteInput = findViewById(R.id.inviteInput);
        btnSubmit = findViewById(R.id.btnSubmit);
        txtSwitchMode = findViewById(R.id.txtSwitchMode);

        // Click listeners cho các tab chuyển đổi
        tabLogin.setOnClickListener(v -> setMode(false));
        tabRegister.setOnClickListener(v -> setMode(true));
        txtSwitchMode.setOnClickListener(v -> setMode(!isRegisterMode));

        // Khi thay đổi vai trò (Sinh viên / Giảng viên) trong chế độ đăng ký
        radioTeacher.setOnCheckedChangeListener((button, checked) -> updateInviteVisibility());
        radioStudent.setOnCheckedChangeListener((button, checked) -> updateInviteVisibility());

        // Nút hành động chính
        btnSubmit.setOnClickListener(v -> {
            if (isRegisterMode) {
                doRegister();
            } else {
                doLogin();
            }
        });

        // Mặc định ở chế độ Đăng nhập
        setMode(false);
    }

    private void updateInviteVisibility() {
        if (isRegisterMode) {
            boolean isTeacher = radioTeacher.isChecked();
            layoutInviteSection.setVisibility(isTeacher ? View.VISIBLE : View.GONE);
            lblId.setText(isTeacher ? "Mã giảng viên" : "Mã sinh viên");
            idInput.setHint(isTeacher ? "Ví dụ: GV01 hoặc MINHVD" : "Ví dụ: B21DCCN123");
        }
    }

    /**
     * Chuyển đổi giữa chế độ Đăng nhập và Đăng ký:
     * - Đăng ký: hiển thị đầy đủ tất cả các trường (Vai trò, Họ tên, Mã, Mật khẩu, Mã mời)
     * - Đăng nhập: chỉ hiển thị các trường cần thiết (Mã định danh, Mật khẩu)
     */
    private void setMode(boolean register) {
        isRegisterMode = register;

        if (register) {
            // Tab Đăng ký được chọn
            tabRegister.setBackgroundResource(R.drawable.bg_tab_item_selected);
            tabRegister.setTextColor(getColor(R.color.brand_primary));
            tabRegister.setTypeface(null, Typeface.BOLD);

            tabLogin.setBackgroundResource(R.drawable.bg_tab_item_unselected);
            tabLogin.setTextColor(getColor(R.color.text_secondary));
            tabLogin.setTypeface(null, Typeface.NORMAL);

            // Hiển thị đầy đủ tất cả các trường
            layoutRoleSection.setVisibility(View.VISIBLE);
            layoutNameSection.setVisibility(View.VISIBLE);
            updateInviteVisibility();

            lblPass.setText("Mật khẩu tạo mới");
            passInput.setHint("Từ 8 ký tự trở lên");

            btnSubmit.setText("Đăng ký tài khoản");
            txtSwitchMode.setText("Đã có tài khoản? Đăng nhập ngay");
        } else {
            // Tab Đăng nhập được chọn
            tabLogin.setBackgroundResource(R.drawable.bg_tab_item_selected);
            tabLogin.setTextColor(getColor(R.color.brand_primary));
            tabLogin.setTypeface(null, Typeface.BOLD);

            tabRegister.setBackgroundResource(R.drawable.bg_tab_item_unselected);
            tabRegister.setTextColor(getColor(R.color.text_secondary));
            tabRegister.setTypeface(null, Typeface.NORMAL);

            // Chỉ hiển thị các trường cần thiết: Mã định danh và Mật khẩu
            layoutRoleSection.setVisibility(View.GONE);
            layoutNameSection.setVisibility(View.GONE);
            layoutInviteSection.setVisibility(View.GONE);

            lblId.setText("Mã sinh viên / giảng viên");
            idInput.setHint("Nhập mã sinh viên hoặc mã giảng viên");
            lblPass.setText("Mật khẩu");
            passInput.setHint("Nhập mật khẩu");

            btnSubmit.setText("Đăng nhập");
            txtSwitchMode.setText("Chưa có tài khoản? Đăng ký ngay");
        }
    }

    private void doLogin() {
        String id = idInput.getText().toString().trim();
        String pass = passInput.getText().toString();
        if (id.isEmpty() || pass.isEmpty()) {
            toast("Vui lòng nhập mã người dùng và mật khẩu");
            return;
        }
        setBusy(true);
        api.login(new Models.LoginRequest(id, pass)).enqueue(authCallback());
    }

    private void doRegister() {
        String id = idInput.getText().toString().trim();
        String name = nameInput.getText().toString().trim();
        String pass = passInput.getText().toString();
        boolean teacher = radioTeacher.isChecked();

        if (!ID_PATTERN.matcher(id).matches()) {
            toast("Mã chỉ gồm chữ, số, dấu . _ - và dài 3-32 ký tự");
            return;
        }
        if (name.length() < 2) {
            toast("Vui lòng nhập họ và tên");
            return;
        }
        if (pass.length() < 8) {
            toast("Mật khẩu phải từ 8 ký tự trở lên");
            return;
        }
        String invite = teacher ? inviteInput.getText().toString().trim() : null;
        if (teacher && (invite == null || invite.isEmpty())) {
            toast("Vui lòng nhập mã mời giảng viên");
            return;
        }
        setBusy(true);
        api.register(new Models.RegisterRequest(id, name, pass, teacher ? "TEACHER" : "STUDENT", invite))
                .enqueue(authCallback());
    }

    private ApiCallback<Models.AuthResponse> authCallback() {
        return new ApiCallback<Models.AuthResponse>(this) {
            @Override
            public void onSuccess(Models.AuthResponse r) {
                store.save(r);
                openMain();
            }

            @Override
            public void onError(String message, String code, int http) {
                setBusy(false);
                toast(message);
            }
        };
    }

    private void setBusy(boolean busy) {
        btnSubmit.setEnabled(!busy);
        btnSubmit.setAlpha(busy ? 0.6f : 1.0f);
        tabLogin.setEnabled(!busy);
        tabRegister.setEnabled(!busy);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
