package com.uni.attendance.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

/** Lấy vị trí hiện tại một lần, từ chối vị trí giả lập. Cần đã được cấp quyền vị trí chính xác. */
public final class LocationHelper {
    private LocationHelper() {}

    public interface Listener {
        void onLocation(double latitude, double longitude);

        void onError(String message);
    }

    @SuppressWarnings({"MissingPermission", "deprecation"})
    public static void current(Context context, Listener listener) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            listener.onError("Chưa được cấp quyền vị trí chính xác");
            return;
        }
        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(context);
        CancellationTokenSource cancel = new CancellationTokenSource();
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancel.getToken())
                .addOnSuccessListener(location -> {
                    if (location == null) {
                        listener.onError("Không lấy được vị trí. Hãy bật GPS rồi thử lại");
                    } else if (isMock(location)) {
                        listener.onError("Phát hiện vị trí giả lập, không thể điểm danh");
                    } else {
                        listener.onLocation(location.getLatitude(), location.getLongitude());
                    }
                })
                .addOnFailureListener(e -> listener.onError(
                        "Không lấy được vị trí. Hãy bật GPS và kiểm tra Google Play Services"));
    }

    @SuppressWarnings("deprecation")
    private static boolean isMock(Location location) {
        return Build.VERSION.SDK_INT >= 31 ? location.isMock() : location.isFromMockProvider();
    }
}
