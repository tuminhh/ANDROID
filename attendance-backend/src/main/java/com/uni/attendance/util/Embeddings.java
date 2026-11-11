package com.uni.attendance.util;

/** Các phép xử lý vector đặc trưng khuôn mặt (không phụ thuộc Spring). */
public final class Embeddings {
    private Embeddings() {}

    /** Trả về bản sao đã chuẩn hóa L2. Ném IllegalArgumentException nếu vector rỗng, bằng 0 hoặc chứa NaN/Infinity. */
    public static float[] normalized(float[] v) {
        if (v == null || v.length == 0) {
            throw new IllegalArgumentException("Vector rỗng");
        }
        double sum = 0;
        for (float x : v) {
            if (!Float.isFinite(x)) {
                throw new IllegalArgumentException("Vector chứa giá trị không hợp lệ");
            }
            sum += (double) x * x;
        }
        double norm = Math.sqrt(sum);
        if (norm < 1e-9) {
            throw new IllegalArgumentException("Vector bằng 0");
        }
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = (float) (v[i] / norm);
        }
        return out;
    }

    /** Độ tương đồng cosine trong khoảng [-1, 1]. */
    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            throw new IllegalArgumentException("Hai vector phải cùng số chiều");
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    public static String serialize(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 12);
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(v[i]);
        }
        return sb.toString();
    }

    public static float[] parse(String s) {
        String[] parts = s.split(",");
        float[] v = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            v[i] = Float.parseFloat(parts[i]);
        }
        return v;
    }
}
