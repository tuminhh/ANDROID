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
import java.io.File;
import androidx.camera.core.ImageCaptureException;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import com.example.smartattendance.api.ApiClient;
import com.example.smartattendance.api.ApiService;
import com.google.gson.JsonObject;

import com.example.smartattendance.api.ApiService;
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

        btnCapture.setEnabled(false);
        btnCapture.setText("Đang xử lý...");

        // 1. Tạo một file tạm thời trong bộ nhớ của ứng dụng để lưu ảnh
        File photoFile = new File(getExternalFilesDir(null), "face_attendance.jpg");
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        // 2. Lệnh chụp ảnh của CameraX
        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                Toast.makeText(CameraActivity.this, "Chụp thành công, đang gửi lên Server...", Toast.LENGTH_SHORT).show();
                // 3. Chụp xong thì gọi hàm Upload
                uploadImageToServer(photoFile);
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e("CameraX", "Lỗi chụp ảnh: " + exception.getMessage(), exception);
                btnCapture.setEnabled(true);
                btnCapture.setText("Chụp ảnh khuôn mặt");
            }
        });
    }

    private void uploadImageToServer(File file) {
        // Đóng gói file ảnh
        RequestBody requestFile = RequestBody.create(MediaType.parse("image/jpeg"), file);
        MultipartBody.Part body = MultipartBody.Part.createFormData("image", file.getName(), requestFile);

        // Kèm theo thông tin người dùng (Ở đây ta tạm hardcode gửi username "SV001", sau này sẽ lấy từ Intent)
        RequestBody username = RequestBody.create(MediaType.parse("text/plain"), "SV001");

        // Gọi API
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        Call<JsonObject> call = apiService.uploadFace(body, username);

        call.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                btnCapture.setEnabled(true);
                btnCapture.setText("Chụp ảnh khuôn mặt");

                if (response.isSuccessful() && response.body() != null) {
                    String status = response.body().get("status").getAsString();
                    String message = response.body().get("message").getAsString();

                    if (status.equals("success")) {
                        Toast.makeText(CameraActivity.this, message, Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        Toast.makeText(CameraActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                btnCapture.setEnabled(true);
                btnCapture.setText("Chụp ảnh khuôn mặt");
                Toast.makeText(CameraActivity.this, "Lỗi mạng khi tải ảnh lên", Toast.LENGTH_SHORT).show();
                Log.e("API_ERROR", t.getMessage());
            }
        });
    }


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