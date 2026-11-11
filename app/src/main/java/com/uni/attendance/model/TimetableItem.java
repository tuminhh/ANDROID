package com.uni.attendance.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;

/**
 * Mục thời khóa biểu đại diện cho một buổi học của môn học trong tuần.
 */
public class TimetableItem implements Serializable {
    /** Thứ trong tuần: 2 (Thứ 2), 3 (Thứ 3), ..., 7 (Thứ 7), 8 (Chủ nhật) */
    @SerializedName("dayOfWeek")
    public int dayOfWeek;

    /** Ngày cụ thể (nếu có, định dạng YYYY-MM-DD hoặc dd/MM) */
    @SerializedName("date")
    public String date;

    /** Ca / Tiết học (ví dụ: "Buổi sáng - 4,5,6", "Tiết 1-3") */
    @SerializedName("shift")
    public String shift;

    /** Tên môn học (ví dụ: "Đảm bảo chất lượng phần mềm") */
    @SerializedName("subjectName")
    public String subjectName;

    /** Mã học phần (ví dụ: "SQA401", "0101004123") */
    @SerializedName("subjectCode")
    public String subjectCode;

    /** Tên giảng viên & thông tin (ví dụ: "Vũ Đình Minh (0912327850 - CNTT&TT)") */
    @SerializedName("lecturer")
    public String lecturer;

    /** Phòng học & cơ sở (ví dụ: "402 - A9 - Cơ sở 1 - Khu A") */
    @SerializedName("room")
    public String room;

    /** Ghi chú bổ sung */
    @SerializedName("note")
    public String note;

    public TimetableItem() {}

    public TimetableItem(int dayOfWeek, String date, String shift, String subjectName,
                         String subjectCode, String lecturer, String room, String note) {
        this.dayOfWeek = dayOfWeek;
        this.date = date;
        this.shift = shift;
        this.subjectName = subjectName;
        this.subjectCode = subjectCode;
        this.lecturer = lecturer;
        this.room = room;
        this.note = note;
    }

    public String getDisplayDay() {
        switch (dayOfWeek) {
            case 2: return "Thứ 2";
            case 3: return "Thứ 3";
            case 4: return "Thứ 4";
            case 5: return "Thứ 5";
            case 6: return "Thứ 6";
            case 7: return "Thứ 7";
            case 8: return "Chủ nhật";
            default: return "Thứ " + dayOfWeek;
        }
    }
}
