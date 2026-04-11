package com.bupt.smartta.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * 一条 MO ↔ TA 站内留言（按 moUsername + taApplicantId 线索归档）。
 */
public class MoTaMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String moUsername;
    private String taApplicantId;
    private String fromUsername;
    private String fromDisplayName;
    /** "MO" 或 "TA" */
    private String fromRole;
    private String toUsername;
    private String body;
    private String sentAt;
    private boolean readByRecipient;

    public MoTaMessage() {}

    public static MoTaMessage create(String moUsername, String taApplicantId,
            String fromUsername, String fromDisplayName, String fromRole,
            String toUsername, String body) {
        MoTaMessage m = new MoTaMessage();
        m.id = UUID.randomUUID().toString();
        m.moUsername = moUsername;
        m.taApplicantId = taApplicantId;
        m.fromUsername = fromUsername;
        m.fromDisplayName = fromDisplayName != null ? fromDisplayName : fromUsername;
        m.fromRole = fromRole;
        m.toUsername = toUsername;
        m.body = body != null ? body : "";
        m.sentAt = Instant.now().toString();
        m.readByRecipient = false;
        return m;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getMoUsername() { return moUsername; }
    public void setMoUsername(String moUsername) { this.moUsername = moUsername; }
    public String getTaApplicantId() { return taApplicantId; }
    public void setTaApplicantId(String taApplicantId) { this.taApplicantId = taApplicantId; }
    public String getFromUsername() { return fromUsername; }
    public void setFromUsername(String fromUsername) { this.fromUsername = fromUsername; }
    public String getFromDisplayName() { return fromDisplayName; }
    public void setFromDisplayName(String fromDisplayName) { this.fromDisplayName = fromDisplayName; }
    public String getFromRole() { return fromRole; }
    public void setFromRole(String fromRole) { this.fromRole = fromRole; }
    public String getToUsername() { return toUsername; }
    public void setToUsername(String toUsername) { this.toUsername = toUsername; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getSentAt() { return sentAt; }
    public void setSentAt(String sentAt) { this.sentAt = sentAt; }
    public boolean isReadByRecipient() { return readByRecipient; }
    public void setReadByRecipient(boolean readByRecipient) { this.readByRecipient = readByRecipient; }
}
