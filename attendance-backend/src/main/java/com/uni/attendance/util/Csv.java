package com.uni.attendance.util;

/** Ghi CSV an toàn, chống chèn công thức khi mở bằng Excel. */
public final class Csv {
    private Csv() {}

    public static String cell(String value) {
        String s = value == null ? "" : value;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    public static String row(String... cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(cell(cells[i]));
        }
        return sb.append("\r\n").toString();
    }
}
