package com.bupt.smartta.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Position implements Serializable {
    private static final long serialVersionUID = 1L;

    private String code;
    private String name;
    private List<String> requiredSkills;
    private int hoursPerWeek;
    private int totalSlots;
    private int filledSlots;
    private String deadline;
    private String description;
    private String postedBy;
    private String postedAt;
    private String status;

    public Position() {
        this.requiredSkills = new ArrayList<>();
        this.status = "Open";
        this.filledSlots = 0;
    }

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
    public String getPostedAt() { return postedAt; }
    public void setPostedAt(String postedAt) { this.postedAt = postedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getRemainingSlots() {
        return Math.max(0, totalSlots - filledSlots);
    }

    public boolean isOpen() {
        return "Open".equals(status) && getRemainingSlots() > 0;
    }

    public String getRequiredSkillsStr() {
        return String.join(", ", requiredSkills);
    }
}
