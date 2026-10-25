package com.example.smartattendance;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class DashboardActivity extends AppCompatActivity {

    private TextView tvWelcome;
    private Button btnOpenCamera, btnLogout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        tvWelcome = findViewById(R.id.tvWelcome);
        btnOpenCamera = findViewById(R.id.btnOpenCamera);
        btnLogout = findViewById(R.id.btnLogout);

        // Nhận dữ liệu
        String name = getIntent().getStringExtra("USER_NAME");
        String role = getIntent().getStringExtra("USER_ROLE");

        if (role != null) {
            String roleName = role.equals("teacher") ? "Giảng viên" : "Sinh viên";
            tvWelcome.setText("Xin chào, " + name + "\n(" + roleName + ")");
        }

        // Mở Camera
        btnOpenCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DashboardActivity.this, CameraActivity.class);
                startActivity(intent);
            }
        });

        // Đăng xuất
        btnLogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(DashboardActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }
}