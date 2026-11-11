package com.uni.attendance.net;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.uni.attendance.BuildConfig;
import com.uni.attendance.ui.LoginActivity;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** Tạo một ApiService dùng chung: tự gắn token và tự đăng xuất khi máy chủ trả 401. */
public final class ApiClient {
    private static ApiService service;

    private ApiClient() {}

    public static synchronized ApiService get(Context context) {
        if (service == null) {
            final Context app = context.getApplicationContext();
            final TokenStore store = TokenStore.get(app);
            final Handler main = new Handler(Looper.getMainLooper());

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .addInterceptor(chain -> {
                        Request request = chain.request();
                        String token = store.token();
                        if (token != null) {
                            request = request.newBuilder().header("Authorization", "Bearer " + token).build();
                        }
                        Response response = chain.proceed(request);
                        if (response.code() == 401 && token != null) {
                            // Token hết hạn hoặc tài khoản không còn: quay về màn đăng nhập
                            store.clear();
                            main.post(() -> {
                                Intent i = new Intent(app, LoginActivity.class);
                                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                app.startActivity(i);
                            });
                        }
                        return response;
                    })
                    .build();

            String base = BuildConfig.BASE_URL;
            if (!base.endsWith("/")) {
                base = base + "/";
            }
            service = new Retrofit.Builder()
                    .baseUrl(base)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(ApiService.class);
        }
        return service;
    }
}
