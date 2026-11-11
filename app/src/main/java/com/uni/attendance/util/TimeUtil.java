package com.uni.attendance.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Đổi chuỗi thời gian ISO-8601 (UTC, dạng 2026-11-05T02:40:33.516Z) của backend sang giờ địa phương. */
public final class TimeUtil {
    private TimeUtil() {}

    public static Date parse(String iso) {
        if (iso == null || iso.length() < 19) return null;
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            in.setTimeZone(TimeZone.getTimeZone("UTC"));
            return in.parse(iso.substring(0, 19));
        } catch (ParseException e) {
            return null;
        }
    }

    public static String display(String iso) {
        Date d = parse(iso);
        if (d == null) return iso == null ? "" : iso;
        return new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(d);
    }

    /** Số phút còn lại tới thời điểm iso (tối thiểu 0). */
    public static long minutesUntil(String iso) {
        Date d = parse(iso);
        if (d == null) return 0;
        return Math.max(0, (d.getTime() - System.currentTimeMillis()) / 60_000);
    }
}
