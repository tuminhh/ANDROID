package com.uni.attendance.web;

import org.springframework.http.HttpStatus;

/** Lỗi nghiệp vụ có mã trạng thái HTTP và mã lỗi máy đọc được (để app Android xử lý). */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
