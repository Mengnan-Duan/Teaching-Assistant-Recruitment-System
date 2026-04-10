package com.bupt.smartta.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SystemLog implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String OP_READ = "READ";
    public static final String OP_WRITE = "WRITE";
    public static final String OP_ERROR = "ERROR";
    public static final String STATUS_OK = "OK";
    public static final String STATUS_FAIL = "FAIL";

    private String timestamp;
    private String operation;
    private String fileName;
    private String status;
    private String detail;

    public SystemLog() {}

    public SystemLog(String operation, String fileName, String status) {
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.operation = operation;
        this.fileName = fileName;
        this.status = status;
    }

    public SystemLog(String operation, String fileName, String status, String detail) {
        this(operation, fileName, status);
        this.detail = detail;
    }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public String getOpIcon() {
        switch (operation) {
            case OP_READ: return "R";
            case OP_WRITE: return "W";
            case OP_ERROR: return "E";
            default: return "?";
        }
    }
}
