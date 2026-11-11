package com.uni.attendance.net;

import android.app.Activity;

import com.google.gson.Gson;

import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Callback chuẩn cho mọi lời gọi API: chạy ở luồng chính, bỏ qua nếu màn hình đã đóng,
 * và đổi lỗi của máy chủ ({"code","message"}) thành thông báo tiếng Việt cho người dùng.
 */
public abstract class ApiCallback<T> implements Callback<T> {
    public static final String CODE_NETWORK = "NETWORK";
    private static final Gson GSON = new Gson();

    private final Activity activity;

    protected ApiCallback(Activity activity) {
        this.activity = activity;
    }

    public abstract void onSuccess(T body);

    /** @param code mã lỗi của backend (ví dụ SESSION_CLOSED) hoặc {@link #CODE_NETWORK}; http = 0 nếu mất kết nối */
    public abstract void onError(String message, String code, int http);

    @Override
    public void onResponse(Call<T> call, Response<T> response) {
        if (gone()) return;
        if (response.isSuccessful()) {
            onSuccess(response.body());
            return;
        }
        String message = "Lỗi máy chủ (" + response.code() + ")";
        String code = "HTTP_" + response.code();
        try {
            String raw = response.errorBody() == null ? "" : response.errorBody().string();
            Models.ApiError error = GSON.fromJson(raw, Models.ApiError.class);
            if (error != null) {
                if (error.code != null) code = error.code;
                if (error.fields != null && !error.fields.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (Map.Entry<String, String> e : error.fields.entrySet()) {
                        if (sb.length() > 0) sb.append('\n');
                        sb.append(e.getValue());
                    }
                    message = sb.toString();
                } else if (error.message != null) {
                    message = error.message;
                }
            }
        } catch (Exception ignored) {
            // Giữ thông báo mặc định nếu thân lỗi không phải JSON
        }
        onError(message, code, response.code());
    }

    @Override
    public void onFailure(Call<T> call, Throwable t) {
        if (gone()) return;
        onError("Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại", CODE_NETWORK, 0);
    }

    private boolean gone() {
        return activity.isFinishing() || activity.isDestroyed();
    }
}
