package com.bupt.smartta.model;

import java.io.Serializable;

/**
 * Represents a TA applicant's application for a specific position.
 *
 * <p>Each application is uniquely identified by the composite key
 * {@code applicantId + "_" + positionCode}. The application tracks the applicant's
 * composite AI score (computed from skill match, GPA, and availability), the
 * MO review status, and the number of times the MO has rejected it (used to
 * implement the permanent-blocking rule after two rejections).</p>
 *
 * <p>Status transitions follow the rules defined in {@link com.bupt.smartta.servlet.ApiServlet}.</p>
 *
 * @see com.bupt.smartta.model.TAPplicant
 * @see com.bupt.smartta.model.Position
 */
public class Application implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Application submitted, awaiting MO review. */
    public static final String STATUS_SUBMITTED = "Submitted";
    /** MO is actively reviewing the application. */
    public static final String STATUS_REVIEW = "Under Review";
    /** MO has accepted the applicant for the position. */
    public static final String STATUS_ACCEPTED = "Accepted";
    /** MO has rejected the application. */
    public static final String STATUS_REJECTED = "Rejected";

    /** Unique application identifier: {@code applicantId + "_" + positionCode}. */
    private String id;
    /** ID of the TA applicant (e.g., "A001"). */
    private String applicantId;
    /** Full name of the applicant at the time of application. */
    private String applicantName;
    /** Code of the position being applied for (e.g., "CST302"). */
    private String positionCode;
    /** Name of the position being applied for. */
    private String positionName;
    /** Date of application in ISO format (yyyy-MM-dd). */
    private String appliedAt;
    /** Current application status. One of: Submitted, Under Review, Accepted, Rejected. */
    private String status;
    /** Overall AI composite score (0-100). */
    private int aiScore;
    /** Score component: skill match (0-100). */
    private int skillScore;
    /** Score component: GPA-based score (0-100). */
    private int gpaScore;
    /** Score component: availability score (0-100). */
    private int availScore;
    /** Human-readable explanation of how the AI score was computed. */
    private String aiExplanation;
    /** LLM-generated match analysis from the Bailian (Qwen) API. */
    private String llmExplanation;
    /**
     * Flag indicating whether this application was previously rejected by an MO.
     * When the TA views the rejected application detail, this flag is cleared
     * to make the position available for re-application.
     */
    private boolean rejectedByMo;
    /**
     * Number of times this application has been rejected by an MO.
     * When this count reaches 2 or more, the applicant is permanently blocked
     * from applying to the same position again.
     */
    private int moRejectionCount;
    /**
     * The number of times this applicant has applied for this position
     * (1 = first application, 2 = re-application after rejection, etc.).
     */
    private int applyCount;

    /**
     * Default constructor required for Jackson deserialization.
     */
    public Application() {}

    /**
     * Creates a new application for a TA applicant.
     *
     * @param applicantId    the TA applicant's unique ID
     * @param applicantName the TA applicant's display name
     * @param positionCode  the code of the position being applied for
     * @param positionName  the name of the position
     * @param aiScore       the pre-computed AI composite score (0-100)
     */
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
        this.applyCount = 1;
    }

    private String buildExplanation() {
        return String.format(
            "Composite score: %d/100%nFormula: 0.4×Skill + 0.3×GPA + 0.3×Availability",
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
    public String getLlmExplanation() { return llmExplanation; }
    public void setLlmExplanation(String llmExplanation) { this.llmExplanation = llmExplanation; }
    public boolean isRejectedByMo() { return rejectedByMo; }
    public void setRejectedByMo(boolean rejectedByMo) { this.rejectedByMo = rejectedByMo; }
    public int getMoRejectionCount() { return moRejectionCount; }
    public void setMoRejectionCount(int moRejectionCount) { this.moRejectionCount = moRejectionCount; }
    public int getApplyCount() { return applyCount; }
    public void setApplyCount(int applyCount) { this.applyCount = applyCount; }

    /**
     * Determines whether the applicant is permanently blocked from re-applying
     * to this position (applicant has been rejected by the MO two or more times).
     *
     * @return {@code true} if permanently blocked, {@code false} otherwise
     */
    public boolean isPermanentlyBlocked() { return moRejectionCount >= 2; }
}
