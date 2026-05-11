package com.bupt.smartta.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a direct message exchanged between a Module Organiser (MO) and a TA applicant.
 *
 * <p>Messages are scoped to a specific MO username and TA applicant ID pair, enabling
 * threaded conversations within the Smart-TA portal. Each message records the sender's
 * role ("MO" or "TA"), the recipient's username, the message body, and a timestamp.</p>
 *
 * <p>Messages are persisted in {@code mota_messages.json} via the
 * {@link com.bupt.smartta.util.DataStore}.</p>
 *
 * @see com.bupt.smartta.util.DataStore
 */
public class MoTaMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    /** System-assigned unique message ID (UUID). */
    private String id;
    /** Username of the MO involved in this conversation. */
    private String moUsername;
    /** Applicant ID of the TA involved in this conversation. */
    private String taApplicantId;
    /** Username of the message sender. */
    private String fromUsername;
    /** Display name of the message sender. */
    private String fromDisplayName;
    /**
     * Role of the sender. One of: "MO" or "TA".
     */
    private String fromRole;
    /** Username of the message recipient. */
    private String toUsername;
    /** Message content text. */
    private String body;
    /** ISO-8601 timestamp when the message was sent. */
    private String sentAt;
    /** Flag indicating whether the recipient has read this message. */
    private boolean readByRecipient;

    /**
     * Default constructor required for Jackson deserialization.
     */
    public MoTaMessage() {}

    /**
     * Factory method that creates a new message with auto-generated ID and timestamp.
     *
     * @param moUsername     the MO's username
     * @param taApplicantId the TA's applicant ID
     * @param fromUsername  the sender's username
     * @param fromDisplayName the sender's display name (fallback to fromUsername if null)
     * @param fromRole       the sender's role ("MO" or "TA")
     * @param toUsername    the recipient's username
     * @param body          the message content
     * @return a new, pre-populated MoTaMessage instance
     */
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
