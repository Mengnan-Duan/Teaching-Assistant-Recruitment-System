package com.bupt.smartta.model;

import java.io.Serializable;

public class Application implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String STATUS_SUBMITTED = "Submitted";
    public static final String STATUS_REVIEW = "Under Review";
    public static final String STATUS_ACCEPTED = "Accepted";
    public static final String STATUS_REJECTED = "Rejected";

    private String id;
    private String applicantId;
    private String applicantName;
    private String positionCode;
    private String positionName;
    private String appliedAt;
    private String status;
    private int aiScore;
    private int skillScore;
    private int gpaScore;
    private int availScore;
    private String aiExplanation;

    public Application() {}

    public Application(String applicantId, String applicantName,
                      String positionCode, String positionName, int aiScore) {
        this.id = applicantId + "_" + positionCode;
        this.applicantId = applicantId;
        this.applicantName = applicantName;
        this.positionCode = positionCode;
        this.positionName = positionName;
        this.appliedAt = java.time.LocalDate.now().toString();
        this.status = STATUS_SUBMITTED;
        this.aiScore = aiScore;
        this.aiExplanation = buildExplanation();
    }

    private String buildExplanation() {
        return String.format(
            "Composite score: %d/100%nFormula: 0.4 Skill + 0.3 GPA + 0.3 Availability",
            aiScore
        );
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getApplicantId() { return applicantId; }
    public void setApplicantId(String applicantId) { this.applicantId = applicantId; }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
    public String getPositionCode() { return positionCode; }
    public void setPositionCode(String positionCode) { this.positionCode = positionCode; }
    public String getPositionName() { return positionName; }
    public void setPositionName(String positionName) { this.positionName = positionName; }
    public String getAppliedAt() { return appliedAt; }
    public void setAppliedAt(String appliedAt) { this.appliedAt = appliedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getAiScore() { return aiScore; }
    public void setAiScore(int aiScore) { this.aiScore = aiScore; }
    public int getSkillScore() { return skillScore; }
    public void setSkillScore(int skillScore) { this.skillScore = skillScore; }
    public int getGpaScore() { return gpaScore; }
    public void setGpaScore(int gpaScore) { this.gpaScore = gpaScore; }
    public int getAvailScore() { return availScore; }
    public void setAvailScore(int availScore) { this.availScore = availScore; }
    public String getAiExplanation() { return aiExplanation; }
    public void setAiExplanation(String aiExplanation) { this.aiExplanation = aiExplanation; }
}
