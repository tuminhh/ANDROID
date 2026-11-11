package com.uni.attendance.face;

import java.util.List;

/** Phép xử lý vector phía app. Việc so khớp do backend đảm nhiệm nên không còn hàm cosine ở đây. */
public final class Embeddings {
    private Embeddings() {}

    public static void normalize(float[] v) {
        double sum = 0;
        for (float x : v) sum += (double) x * x;
        float norm = (float) Math.sqrt(sum);
        if (norm == 0) return;
        for (int i = 0; i < v.length; i++) v[i] /= norm;
    }

    /** Trung bình nhiều mẫu rồi chuẩn hóa L2. */
    public static float[] average(List<float[]> samples) {
        float[] avg = new float[samples.get(0).length];
        for (float[] s : samples) {
            for (int i = 0; i < avg.length; i++) avg[i] += s[i];
        }
        for (int i = 0; i < avg.length; i++) avg[i] /= samples.size();
        normalize(avg);
        return avg;
    }
}
