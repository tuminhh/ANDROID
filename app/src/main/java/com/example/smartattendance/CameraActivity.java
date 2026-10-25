package com.example.smartattendance;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;

public class CameraActivity extends AppCompatActivity {

    private PreviewView viewFinder;
    private Button btnCapture;
    private ImageCapture imageCapture;

    // Mã request code để xin quyền camera
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        viewFinder = findViewById(R.id.viewFinder);
        btnCapture = findViewById(R.id.btnCapture);

        // Kiểm tra quyền Camera, nếu có rồi thì mở, chưa có thì xin quyền
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        // Bắt sự kiện bấm nút chụp ảnh
        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePhoto();
            }
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                // Khởi tạo CameraProvider
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Cấu hình Preview (Hiển thị hình ảnh)
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                // Cấu hình ImageCapture (Dùng để chụp ảnh)
                imageCapture = new ImageCapture.Builder().build();

                // Chọn Camera trước (FRONT) để điểm danh khuôn mặt
                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                // Hủy các liên kết cũ và liên kết Camera với Lifecycle của Activity
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);

            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraX", "Lỗi khởi động camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        if (imageCapture == null) return;

        Toast.makeText(this, "Đang chụp và chuẩn bị gửi lên Server...", Toast.LENGTH_SHORT).show();
        // Todo: Chụp ảnh, crop khuôn mặt và gọi API gửi lên Server bằng Retrofit.
    }

    // --- Các hàm hỗ trợ kiểm tra quyền ---
    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Ứng dụng cần quyền Camera để điểm danh", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}