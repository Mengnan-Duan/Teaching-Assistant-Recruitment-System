package com.bupt.smartta.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Represents a system audit log entry recording data access and modification events.
 *
 * <p>Each log entry captures a timestamp, the operation type, the affected file,
 * a status indicator, and optional detail text. Logs are stored in
 * {@code system_logs.json} and are used for auditing, debugging, and data
 * traceability purposes.</p>
 *
 * <p>Operation types: {@link #OP_READ}, {@link #OP_WRITE}, {@link #OP_ERROR}.</p>
 *
 * @see com.bupt.smartta.util.DataStore
 */
public class SystemLog implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Operation type: read access to a data file. */
    public static final String OP_READ = "READ";
    /** Operation type: write/update to a data file. */
    public static final String OP_WRITE = "WRITE";
    /** Operation type: error or failure. */
    public static final String OP_ERROR = "ERROR";
    /** Status code: operation completed successfully. */
    public static final String STATUS_OK = "OK";
    /** Status code: operation failed. */
    public static final String STATUS_FAIL = "FAIL";

    /** Timestamp in format "yyyy-MM-dd HH:mm:ss". */
    private String timestamp;
    /** Operation type: READ, WRITE, or ERROR. */
    private String operation;
    /** Name of the affected data file. */
    private String fileName;
    /** Operation result status: OK or FAIL. */
    private String status;
    /** Additional detail text (may be null). */
    private String detail;

    /**
     * Default constructor required for Jackson deserialization.
     */
    public SystemLog() {}

    /**
     * Convenience constructor that auto-populates the timestamp.
     *
     * @param operation the type of operation (READ, WRITE, ERROR)
     * @param fileName the affected data file name
     * @param status   the result status (OK, FAIL)
     */
    public SystemLog(String operation, String fileName, String status) {
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        this.operation = operation;
        this.fileName = fileName;
        this.status = status;
    }

    /**
     * Full constructor including detail text.
     *
     * @param operation the type of operation (READ, WRITE, ERROR)
     * @param fileName the affected data file name
     * @param status   the result status (OK, FAIL)
     * @param detail   additional detail or error message
     */
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

    /**
     * Returns a single-character icon for the operation type.
     * Ignored by Jackson serialization.
     *
     * @return "R" for READ, "W" for WRITE, "E" for ERROR, "?" otherwise
     */
    @JsonIgnore
    public String getOpIcon() {
        if (operation == null) return "?";
        switch (operation) {
            case OP_READ: return "R";
            case OP_WRITE: return "W";
            case OP_ERROR: return "E";
            default: return "?";
        }
    }
}
