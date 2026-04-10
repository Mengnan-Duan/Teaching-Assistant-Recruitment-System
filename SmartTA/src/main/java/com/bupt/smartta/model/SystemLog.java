package com.bupt.smartta.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SystemLog implements Serializable {
    private static final long serialVersionUID = 1L;

    // 操作类型常量
    public static final String OP_READ = "READ";
    public static final String OP_WRITE = "WRITE";
    public static final String OP_ERROR = "ERROR";
    public static final String OP_DELETE = "DELETE";
    public static final String OP_UPDATE = "UPDATE";
    public static final String OP_LOGIN = "LOGIN";
    public static final String OP_LOGOUT = "LOGOUT";

    // 状态常量
    public static final String STATUS_OK = "OK";
    public static final String STATUS_FAIL = "FAIL";

    // 日志级别常量
    public static final String LEVEL_DEBUG = "DEBUG";
    public static final String LEVEL_INFO = "INFO";
    public static final String LEVEL_WARN = "WARN";
    public static final String LEVEL_ERROR = "ERROR";

    private String timestamp;
    private String operation;
    private String fileName;
    private String status;
    private String detail;
    private String level;
    private String userId;
    private String ipAddress;
    private String sessionId;

    public SystemLog() {}

    public SystemLog(String operation, String fileName, String status) {
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.operation = operation;
        this.fileName = fileName;
        this.status = status;
        this.level = LEVEL_INFO; // 默认日志级别为INFO
    }

    public SystemLog(String operation, String fileName, String status, String detail) {
        this(operation, fileName, status);
        this.detail = detail;
    }

    public SystemLog(String operation, String fileName, String status, String detail, String level) {
        this(operation, fileName, status, detail);
        this.level = level;
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
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getOpIcon() {
        switch (operation) {
            case OP_READ: return "R";
            case OP_WRITE: return "W";
            case OP_ERROR: return "E";
            default: return "?";
        }
    }
}
