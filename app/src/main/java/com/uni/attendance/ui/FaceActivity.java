package com.uni.attendance.ui;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.Tasks;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.uni.attendance.R;
import com.uni.attendance.face.Embeddings;
import com.uni.attendance.face.FaceEmbedder;
import com.uni.attendance.net.ApiCallback;
import com.uni.attendance.net.ApiClient;
import com.uni.attendance.net.ApiService;
import com.uni.attendance.net.Models;
import com.uni.attendance.util.LocationHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Màn hình camera dùng chung cho hai chế độ. App chỉ trích vector khuôn mặt, còn so khớp do backend làm.
 *  - MODE_ENROLL: lấy nhiều mẫu, gửi vector trung bình lên PUT /api/face
 *  - MODE_ATTEND: thử thách sống (chớp mắt, rồi quay đầu theo hướng NGẪU NHIÊN, rồi nhìn thẳng), lấy vị trí nếu buổi
 *    yêu cầu, rồi gửi POST /api/attendance. Hướng ngẫu nhiên khiến video quay sẵn khó đáp ứng được.
 */
public class FaceActivity extends AppCompatActivity {
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_SESSION_ID = "sessionId";
    public static final String EXTRA_LOCATION_REQUIRED = "locationRequired";
    public static final String EXTRA_PASSWORD = "password"; // chỉ khi đăng ký lại khuôn mặt
    public static final int MODE_ENROLL = 0;
    public static final int MODE_ATTEND = 1;

    private static final int ENROLL_SAMPLES = 5;
    private static final float TURN_ANGLE = 20f;       // độ lệch tối thiểu để tính là đã quay đầu
    private static final long CHALLENGE_TIMEOUT_MS = 15_000;
    /**
     * Dấu của headEulerAngleY ứng với "quay sang phải của người dùng". Chưa kiểm chứng trên máy thật:
     * nếu khi chạy thử thấy gợi ý "phải" nhưng phải quay sang trái mới qua, hãy đổi giá trị này thành -1.
     */
    private static final int RIGHT_TURN_SIGN = 1;

    private static final int STAGE_BLINK = 0;
    private static final int STAGE_TURN = 1;
    private static final int STAGE_FINAL = 2;

    private PreviewView previewView;
    private TextView hintView;
    private FaceDetector detector;
    private FaceEmbedder embedder;
    private ExecutorService executor;
    private ApiService api;

    private int mode;
    private long sessionId;
    private boolean needLocation;
    private String password;

    // Các cờ này được ghi từ cả luồng phân tích ảnh và luồng chính
    private volatile boolean finished = false;
    private volatile boolean busy = false;
    private volatile boolean eyesOpenSeen = false;
    private volatile boolean blinked = false;
    private volatile int stage = STAGE_BLINK;
    private volatile int turnDirection = 1;             // 1 = phải, -1 = trái (theo người dùng)
    private volatile long challengeStartedAt = 0;
    private final Random random = new Random();
    private final List<float[]> samples = new ArrayList<>();
    private long lastSampleAt = 0;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (!Boolean.TRUE.equals(result.get(Manifest.permission.CAMERA))) {
                    toast("Cần cấp quyền camera để tiếp tục");
                    finish();
                    return;
                }
                if (needLocation && !Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))) {
                    toast("Buổi này yêu cầu vị trí: hãy cấp quyền vị trí chính xác");
                    finish();
                    return;
                }
                startCamera();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_face);
        previewView = findViewById(R.id.previewView);
        hintView = findViewById(R.id.hintView);
        api = ApiClient.get(this);

        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_ATTEND);
        sessionId = getIntent().getLongExtra(EXTRA_SESSION_ID, -1);
        needLocation = getIntent().getBooleanExtra(EXTRA_LOCATION_REQUIRED, false);
        password = getIntent().getStringExtra(EXTRA_PASSWORD);
        if (mode == MODE_ATTEND && sessionId < 0) {
            finish();
            return;
        }

        try {
            embedder = new FaceEmbedder(this);
        } catch (Exception e) {
            toast("Không nạp được mô hình nhận diện: " + e.getMessage());
            finish();
            return;
        }

        detector = FaceDetection.getClient(new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(0.3f)
                .build());
        executor = Executors.newSingleThreadExecutor();

        hintView.setText(mode == MODE_ENROLL
                ? "Nhìn thẳng vào camera" : "Nhìn thẳng và chớp mắt một lần");
        resetChallenge();
        permissionLauncher.launch(needLocation
                ? new String[]{Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}
                : new String[]{Manifest.permission.CAMERA});
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                analysis.setAnalyzer(executor, this::analyze);
                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis);
            } catch (Exception e) {
                toast("Không mở được camera");
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyze(ImageProxy proxy) {
        if (finished || busy) {
            proxy.close();
            return;
        }
        try {
            Bitmap raw = proxy.toBitmap();
            Matrix matrix = new Matrix();
            matrix.postRotate(proxy.getImageInfo().getRotationDegrees());
            Bitmap frame = Bitmap.createBitmap(raw, 0, 0, raw.getWidth(), raw.getHeight(), matrix, true);

            List<Face> faces = Tasks.await(detector.process(InputImage.fromBitmap(frame, 0)));
            if (faces.size() != 1) {
                setHint(faces.isEmpty() ? "Không thấy khuôn mặt" : "Chỉ để một khuôn mặt trong khung");
                return;
            }
            handleFace(frame, faces.get(0));
        } catch (Exception ignored) {
            // Bỏ qua khung hình lỗi, xử lý khung tiếp theo
        } finally {
            proxy.close();
        }
    }

    private void handleFace(Bitmap frame, Face face) {
        if (mode == MODE_ATTEND && stage == STAGE_TURN) {
            turnStep(face);
            return;
        }
        boolean frontal = Math.abs(face.getHeadEulerAngleY()) < 12
                && Math.abs(face.getHeadEulerAngleX()) < 12;
        if (!frontal) {
            setHint("Hãy nhìn thẳng vào camera");
            return;
        }
        Rect box = face.getBoundingBox();
        int left = Math.max(0, box.left);
        int top = Math.max(0, box.top);
        int w = Math.min(frame.getWidth(), box.right) - left;
        int h = Math.min(frame.getHeight(), box.bottom) - top;
        if (w <= 0 || h <= 0) return;
        Bitmap crop = Bitmap.createBitmap(frame, left, top, w, h);

        if (mode == MODE_ENROLL) {
            enrollStep(crop);
        } else {
            attendStep(crop, face);
        }
    }

    // ---------------- Đăng ký khuôn mặt ----------------
    private void enrollStep(Bitmap crop) {
        long now = System.currentTimeMillis();
        if (now - lastSampleAt < 400) return;
        lastSampleAt = now;
        samples.add(embedder.embed(crop));
        setHint("Đang quét... " + samples.size() + "/" + ENROLL_SAMPLES);
        if (samples.size() < ENROLL_SAMPLES) return;

        busy = true;
        float[] average = Embeddings.average(samples);
        setHint("Đang gửi lên máy chủ...");
        runOnUiThread(() -> api.enrollFace(new Models.FaceEnrollRequest(average, password))
                .enqueue(new ApiCallback<Void>(this) {
                    @Override
                    public void onSuccess(Void body) {
                        finished = true;
                        toast("Đăng ký khuôn mặt thành công");
                        finish();
                    }

                    @Override
                    public void onError(String message, String code, int http) {
                        finished = true;
                        toast(message);
                        finish();
                    }
                }));
    }

    // ---------------- Điểm danh ----------------
    private void attendStep(Bitmap crop, Face face) {
        Float left = face.getLeftEyeOpenProbability();
        Float right = face.getRightEyeOpenProbability();
        if (left == null || right == null) return;
        float open = (left + right) / 2f;

        if (timedOut()) {
            resetChallenge();
            setHint("Hết thời gian, thử lại: hãy chớp mắt một lần");
            return;
        }
        if (stage == STAGE_BLINK) {
            if (open > 0.7f) eyesOpenSeen = true;
            if (eyesOpenSeen && open < 0.3f) {
                blinked = true;
                stage = STAGE_TURN;
                setHint(turnPrompt());
            } else {
                setHint("Hãy chớp mắt một lần");
            }
            return;
        }
        // STAGE_FINAL: đã quay đầu xong và đang nhìn thẳng (handleFace đã kiểm tra)
        if (open < 0.7f) return; // đợi mở mắt hẳn rồi mới lấy mẫu

        busy = true; // dừng xử lý khung hình trong lúc gửi lên máy chủ
        float[] embedding = embedder.embed(crop);
        setHint("Đang xác minh...");
        runOnUiThread(() -> {
            if (!needLocation) {
                submitAttendance(embedding, null, null);
                return;
            }
            LocationHelper.current(this, new LocationHelper.Listener() {
                @Override
                public void onLocation(double latitude, double longitude) {
                    submitAttendance(embedding, latitude, longitude);
                }

                @Override
                public void onError(String message) {
                    retry(message);
                }
            });
        });
    }

    /** Giai đoạn quay đầu: chờ góc Y đủ lớn đúng hướng được yêu cầu, rồi chuyển sang bước nhìn thẳng. */
    private void turnStep(Face face) {
        if (timedOut()) {
            resetChallenge();
            setHint("Hết thời gian, thử lại: hãy chớp mắt một lần");
            return;
        }
        float y = face.getHeadEulerAngleY() * RIGHT_TURN_SIGN * turnDirection;
        if (y > TURN_ANGLE) {
            stage = STAGE_FINAL;
            setHint("Tốt, giờ hãy nhìn thẳng vào camera");
        } else {
            setHint(turnPrompt());
        }
    }

    private String turnPrompt() {
        return turnDirection > 0 ? "Hãy quay đầu sang bên PHẢI của bạn" : "Hãy quay đầu sang bên TRÁI của bạn";
    }

    private boolean timedOut() {
        return System.currentTimeMillis() - challengeStartedAt > CHALLENGE_TIMEOUT_MS;
    }

    /** Bắt đầu lại từ chớp mắt, hướng quay mới được chọn ngẫu nhiên. */
    private void resetChallenge() {
        eyesOpenSeen = false;
        blinked = false;
        stage = STAGE_BLINK;
        turnDirection = random.nextBoolean() ? 1 : -1;
        challengeStartedAt = System.currentTimeMillis();
    }

    private void submitAttendance(float[] embedding, Double latitude, Double longitude) {
        api.attend(new Models.AttendanceRequest(sessionId, embedding, latitude, longitude))
                .enqueue(new ApiCallback<Models.AttendanceResult>(this) {
                    @Override
                    public void onSuccess(Models.AttendanceResult r) {
                        if (r.matched) {
                            finished = true;
                            toast(r.similarity == null ? "Điểm danh thành công"
                                    : String.format("Điểm danh thành công (độ khớp %.0f%%)", r.similarity * 100));
                            finish();
                        } else if (r.attemptsLeft != null && r.attemptsLeft <= 0) {
                            finished = true;
                            toast(r.message);
                            finish();
                        } else {
                            retry(r.message + ". Hãy chớp mắt để thử lại");
                        }
                    }

                    @Override
                    public void onError(String message, String code, int http) {
                        if (ApiCallback.CODE_NETWORK.equals(code)) {
                            retry(message);
                        } else {
                            finished = true; // buổi đóng, đã điểm danh, ngoài phạm vi, hết lượt...
                            toast(message);
                            finish();
                        }
                    }
                });
    }

    /** Cho phép thử lại: làm lại toàn bộ thử thách (hướng quay mới) rồi mới lấy mẫu mới. */
    private void retry(String message) {
        resetChallenge();
        setHint(message);
        busy = false;
    }

    private void setHint(String text) {
        runOnUiThread(() -> hintView.setText(text));
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        finished = true;
        if (executor != null) executor.shutdown();
        if (detector != null) detector.close();
        if (embedder != null) embedder.close();
    }
}
