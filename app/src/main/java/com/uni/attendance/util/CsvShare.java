package com.uni.attendance.util;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import okhttp3.ResponseBody;

/** Lưu file CSV hoặc Excel tải từ máy chủ vào thư mục tạm rồi mở hộp thoại chia sẻ (Excel, Drive, Zalo...). */
public final class CsvShare {
    private CsvShare() {}

    /** fileName chỉ nên gồm chữ, số, dấu - và _ (do app tự đặt, không lấy từ người dùng). */
    public static void share(Activity activity, ResponseBody body, String fileName) throws IOException {
        File dir = new File(activity.getCacheDir(), "exports");
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Không tạo được thư mục tạm");
        }
        File file = new File(dir, fileName);
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(body.bytes());
        }
        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", file);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(fileName.endsWith(".xlsx")
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" : "text/csv");
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(Intent.createChooser(send, "Chia sẻ bảng điểm danh"));
    }
}
