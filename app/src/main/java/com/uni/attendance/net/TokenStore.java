package com.uni.attendance.net;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

/**
 * Lưu token và thông tin đăng nhập, mã hóa bằng Android Keystore.
 * Nếu thiết bị không tạo được khóa mã hóa thì chỉ giữ trong bộ nhớ (đóng app là phải đăng nhập lại),
 * chứ không hạ xuống lưu rõ.
 */
public final class TokenStore {
    private static TokenStore instance;

    private final SharedPreferences prefs; // null nếu không dùng được Keystore
    private String token, userId, name, role;

    public static synchronized TokenStore get(Context context) {
        if (instance == null) {
            instance = new TokenStore(context.getApplicationContext());
        }
        return instance;
    }

    private TokenStore(Context context) {
        SharedPreferences p;
        try {
            MasterKey key = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            p = EncryptedSharedPreferences.create(context, "secure_session", key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            p = null;
        }
        prefs = p;
        if (prefs != null) {
            token = prefs.getString("token", null);
            userId = prefs.getString("userId", null);
            name = prefs.getString("name", null);
            role = prefs.getString("role", null);
        }
    }

    public synchronized void save(Models.AuthResponse r) {
        token = r.token;
        userId = r.user.userId;
        name = r.user.name;
        role = r.user.role;
        if (prefs != null) {
            prefs.edit().putString("token", token).putString("userId", userId)
                    .putString("name", name).putString("role", role).apply();
        }
    }

    public synchronized void clear() {
        token = userId = name = role = null;
        if (prefs != null) {
            prefs.edit().clear().apply();
        }
    }

    public synchronized boolean isLoggedIn() { return token != null; }
    public synchronized String token() { return token; }
    public synchronized String userId() { return userId == null ? "" : userId; }
    public synchronized String name() { return name == null ? "" : name; }
    public synchronized boolean isTeacher() { return "TEACHER".equals(role); }
}
