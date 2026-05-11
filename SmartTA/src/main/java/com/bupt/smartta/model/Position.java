package com.bupt.smartta.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a TA position (job posting) created by a Module Organiser (MO).
 *
 * <p>Each position has a unique code, a name, a list of required skills, a weekly
 * hour commitment, a total number of available slots, a deadline, and a current
 * status. Slots are filled as TAs are accepted through the application process.</p>
 *
 * <p>Positions are persisted in {@code positions.json} and are indexed by code
 * in the {@link com.bupt.smartta.util.DataStore}.</p>
 *
 * @see com.bupt.smartta.model.Application
 * @see com.bupt.smartta.model.TAPplicant
 */
public class Position implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Unique position code (e.g., "CST302"). */
    private String code;
    /** Display name of the position. */
    private String name;
    /** List of skills required for this position. */
    private List<String> requiredSkills;
    /** Required weekly hours for this position. */
    private int hoursPerWeek;
    /** Total number of TA slots for this position. */
    private int totalSlots;
    /** Number of slots already filled by accepted TAs. */
    private int filledSlots;
    /** Application deadline in ISO format (yyyy-MM-dd). */
    private String deadline;
    /** Detailed description of the position. */
    private String description;
    /** Display name of the MO who posted this position. */
    private String postedBy;
    /**
     * Username of the MO who posted this position (written by the system,
     * used to link TAs precisely to the responsible MO).
     */
    private String postedByUsername;
    /** Date when the position was posted in ISO format (yyyy-MM-dd). */
    private String postedAt;
    /** Current status of the position. One of: "Open", "Closed", etc. */
    private String status;
    /** Jackson-serialised field reflecting the open/closed state. */
    private boolean open;
    /**
     * Legacy field for backward compatibility with old JSON structures.
     * Jackson ignores this field during serialization.
     */
    private String requiredSkillsStr;
    /**
     * Legacy field for backward compatibility with old JSON structures.
     * Jackson ignores this field during serialization.
     */
    private int remainingSlots;

    /**
     * Default constructor. Initialises required skills to an empty list,
     * status to "Open", and fills remaining slots to zero.
     */
    public Position() {
        this.requiredSkills = new ArrayList<>();
        this.status = "Open";
        this.filledSlots = 0;
        this.remainingSlots = 0;
        this.open = true;
    }

    /**
     * Creates a new position with all required fields.
     *
     * @param code          the unique position code
     * @param name         the display name
     * @param requiredSkills list of required skills (may be null)
     * @param hoursPerWeek required weekly hours
     * @param totalSlots   total number of TA slots
     * @param deadline     application deadline in ISO format
     * @param postedBy     display name of the posting MO
     */
    public Position(String code, String name, List<String> requiredSkills,
                   int hoursPerWeek, int totalSlots, String deadline, String postedBy) {
        this.code = code;
        this.name = name;
        this.requiredSkills = requiredSkills != null ? requiredSkills : new ArrayList<>();
        this.hoursPerWeek = hoursPerWeek;
        this.totalSlots = totalSlots;
        this.deadline = deadline;
        this.postedBy = postedBy;
        this.postedAt = java.time.LocalDate.now().toString();
        this.status = "Open";
        this.filledSlots = 0;
        this.remainingSlots = totalSlots;
        this.open = true;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<String> getRequiredSkills() { return requiredSkills; }
    public void setRequiredSkills(List<String> requiredSkills) { this.requiredSkills = requiredSkills; }
    public int getHoursPerWeek() { return hoursPerWeek; }
    public void setHoursPerWeek(int hoursPerWeek) { this.hoursPerWeek = hoursPerWeek; }
    public int getTotalSlots() { return totalSlots; }
    public void setTotalSlots(int totalSlots) { this.totalSlots = totalSlots; }
    public int getFilledSlots() { return filledSlots; }
    public void setFilledSlots(int filledSlots) { this.filledSlots = filledSlots; }
    public String getDeadline() { return deadline; }
    public void setDeadline(String deadline) { this.deadline = deadline; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getPostedBy() { return postedBy; }
    public void setPostedBy(String postedBy) { this.postedBy = postedBy; }
    public String getPostedByUsername() { return postedByUsername; }
    public void setPostedByUsername(String postedByUsername) { this.postedByUsername = postedByUsername; }
    public String getPostedAt() { return postedAt; }
    public void setPostedAt(String postedAt) { this.postedAt = postedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    /**
     * Dynamically evaluates whether this position is open.
     * A position is open only when its status is "Open" and remaining slots are greater than zero.
     *
     * @return {@code true} if the position is open and accepting applications
     */
    public boolean isOpen() {
        return "Open".equals(status) && getRemainingSlots() > 0;
    }

    /**
     * Jackson serialization accessor for the {@code open} field (for backward JSON compatibility).
     *
     * @return the raw value of the {@code open} field
     */
    @JsonProperty("open")
    public boolean isOpenForJackson() { return open; }

    public void setOpen(boolean open) { this.open = open; }

    /**
     * Returns the required skills as a comma-separated string.
     * Ignored by Jackson serialization.
     *
     * @return comma-separated skills, or an empty string if none
     */
    @JsonIgnore
    public String getRequiredSkillsStr() {
        if (requiredSkills == null || requiredSkills.isEmpty()) {
            return "";
        }
        return String.join(", ", requiredSkills);
    }
    public void setRequiredSkillsStr(String requiredSkillsStr) { this.requiredSkillsStr = requiredSkillsStr; }

    /**
     * Computes the number of remaining (unfilled) slots.
     * Ignored by Jackson serialization (computed dynamically).
     *
     * @return total slots minus filled slots, never negative
     */
    @JsonIgnore
    public int getRemainingSlots() {
        return Math.max(0, totalSlots - filledSlots);
    }
    public void setRemainingSlots(int remainingSlots) { this.remainingSlots = remainingSlots; }

    /**
     * Alias for {@link #isOpen()} — checks if the position is currently open for applications.
     *
     * @return {@code true} if open and with remaining slots
     */
    @JsonIgnore
    public boolean isOpenStatus() {
        return "Open".equals(status) && getRemainingSlots() > 0;
    }

    /**
     * Returns the required skills as a comma-separated string (computed dynamically).
     * Ignored by Jackson serialization.
     *
     * @return comma-separated skills, or an empty string if none
     */
    @JsonIgnore
    public String getRequiredSkillsStrComputed() {
        if (requiredSkills == null || requiredSkills.isEmpty()) {
            return "";
        }
        return String.join(", ", requiredSkills);
    }
}
